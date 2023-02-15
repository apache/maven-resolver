package org.eclipse.aether.internal.impl;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Enhanced Local Repository configuration holder.
 */
class EnhancedLocalRepositoryConfig
{
    private static final Logger LOGGER = LoggerFactory.getLogger( EnhancedLocalRepositoryConfig.class );

    private static final String CONFIG_PROP_TRACKING_FILENAME = "aether.enhancedLocalRepository.trackingFilename";

    private static final String DEFAULT_TRACKING_FILENAME = "_remote.repositories";

    protected static final String CONF_PROP_SPLIT = "aether.enhancedLocalRepository.split";

    protected static final String DEFAULT_SPLIT = "false";

    private Properties config;

    EnhancedLocalRepositoryConfig( RepositorySystemSession session, File basedir )
    {
        try
        {
            config = loadConfiguration( basedir );
            int configHash0 = configurationHash( config );
            populateConfiguration( config, session );
            int configHash1 = configurationHash( config );

            if ( configHash0 != configHash1 )
            {
                storeConfig( config, basedir );
            }
            else
            {
                LOGGER.debug( "Use saved ELRM configuration" );
            }
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }
    }

    private static void populateConfiguration( Properties configurations, RepositorySystemSession session )
    {
        populateConfiguration( configurations, session, CONFIG_PROP_TRACKING_FILENAME, DEFAULT_TRACKING_FILENAME );
        populateConfiguration( configurations, session, CONF_PROP_SPLIT, DEFAULT_SPLIT );
    }

    private static void populateConfiguration( Properties configurations, RepositorySystemSession session, String key,
                                               String defaultValue )
    {
        String valueConf = configurations.getProperty( key );
        String valueSession = ConfigUtils.getString( session, null, key );

        if ( StringUtils.isNotBlank( valueConf ) && StringUtils.isNotBlank( valueSession )
            && !valueConf.equals( valueSession ) )
        {
            LOGGER.debug( "New config {}={} for ELRM will not be used", key, valueSession );
        }

        if ( StringUtils.isBlank( valueConf ) && StringUtils.isNotBlank( valueSession ) )
        {
            configurations.setProperty( key, valueSession );
        }
        else if ( StringUtils.isBlank( valueConf ) && StringUtils.isBlank( valueSession ) )
        {
            configurations.setProperty( key, defaultValue );
        }
    }

    private static int configurationHash( Properties configurations )
    {
        return configurations.entrySet().stream()
            .mapToInt( e -> Objects.hash( e.getKey(), e.getValue() ) )
            .reduce( 1, ( i1, i2 ) -> 31 * i1 * i2 );
    }

    private static Properties loadConfiguration( File basedir ) throws IOException
    {
        Properties props = new Properties();
        Path configPath = Optional.ofNullable( basedir )
            .map( File::toPath )
            .map( p -> p.resolve( "elrm.properties" ) )
            .filter( Files::isReadable )
            .orElse( null );

        if ( configPath != null )
        {
            try ( InputStream inputStream = Files.newInputStream( configPath ) )
            {
                props.load( inputStream );
            }
        }
        return props;
    }

    private void storeConfig( Properties configurations, File basedir ) throws IOException
    {
        Path configPath = Optional.ofNullable( basedir )
            .map( File::toPath )
            .map( p -> p.resolve( "elrm.properties" ) )
            .orElse( null );

        if ( configPath != null )
        {
            Path parent = configPath.getParent();
            if ( parent != null )
            {
                Files.createDirectories( parent );
            }

            LOGGER.debug( "Create local repository configuration: {}", configPath );
            try ( OutputStream outputStream = Files.newOutputStream( configPath, WRITE, TRUNCATE_EXISTING, CREATE ) )
            {
                configurations.store( outputStream, "Enhanced Local Repository Configuration" );
            }
        }
    }

    public String getTrackingFilename()
    {
        return config.getProperty( CONFIG_PROP_TRACKING_FILENAME );
    }

    public boolean isSplit()
    {
        return Boolean.parseBoolean( config.getProperty( CONF_PROP_SPLIT ) );
    }
}
