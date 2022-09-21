package org.eclipse.aether.internal.impl.checksum;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ProvidedChecksumsSource;
import org.eclipse.aether.util.ConfigUtils;

import static java.util.Objects.requireNonNull;

/**
 * Support class for implementing {@link ProvidedChecksumsSource} backed by local filesystem.
 *
 * @since TBD
 */
abstract class FileProvidedChecksumsSourceSupport
        implements ProvidedChecksumsSource
{
    static final String CONFIG_PROP_PREFIX = "aether.artifactResolver.providedChecksumsSource.";

    static final String LOCAL_REPO_PREFIX = ".checksums";

    private final String name;

    FileProvidedChecksumsSourceSupport( String name )
    {
        this.name = requireNonNull( name );
    }

    protected String getName()
    {
        return name;
    }

    @Override
    public Map<String, String> getProvidedArtifactChecksums( RepositorySystemSession session,
                                                             ArtifactDownload transfer,
                                                             List<ChecksumAlgorithmFactory> checksumAlgorithmFactories )
    {
        Path baseDir = getBaseDir( session );
        if ( baseDir != null && !checksumAlgorithmFactories.isEmpty() )
        {
            Map<String, String> result =  performLookup(
                    session, baseDir, transfer.getArtifact(), checksumAlgorithmFactories );

            return result == null || result.isEmpty() ? null : result;
        }
        return null;
    }

    protected abstract Map<String, String> performLookup( RepositorySystemSession session,
                                                          Path baseDir, Artifact artifact,
                                                          List<ChecksumAlgorithmFactory> checksumAlgorithmFactories );

    protected String configPropKey( String name )
    {
        return CONFIG_PROP_PREFIX + getName() + "." + name;
    }

    private Path getBaseDir( RepositorySystemSession session )
    {
        final String baseDirPath = ConfigUtils.getString( session, null, configPropKey( "baseDir" ) );
        final Path baseDir;
        if ( baseDirPath != null )
        {
            baseDir = Paths.get( baseDirPath );
        }
        else
        {
            baseDir = session.getLocalRepository().getBasedir().toPath().resolve( LOCAL_REPO_PREFIX );
        }
        if ( !Files.isDirectory( baseDir ) )
        {
            return null;
        }
        return baseDir;
    }
}
