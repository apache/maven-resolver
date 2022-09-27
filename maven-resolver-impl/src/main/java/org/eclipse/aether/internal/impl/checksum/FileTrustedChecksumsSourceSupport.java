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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.DirectoryUtils;

import static java.util.Objects.requireNonNull;

/**
 * Support class for implementing {@link TrustedChecksumsSource} backed by local filesystem.
 *
 * @since TBD
 */
abstract class FileTrustedChecksumsSourceSupport
        implements TrustedChecksumsSource
{
    private static final String CONFIG_PROP_PREFIX = "aether.trustedChecksumsSource.";

    private static final String CONF_NAME_ENABLED = "enabled";

    /**
     * Visible for testing.
     */
    static final String LOCAL_REPO_PREFIX = ".checksums";

    private final String name;

    FileTrustedChecksumsSourceSupport( String name )
    {
        this.name = requireNonNull( name );
    }

    protected String getName()
    {
        return name;
    }

    @Override
    public Map<String, String> getTrustedArtifactChecksums( RepositorySystemSession session,
                                                            Artifact artifact,
                                                            ArtifactRepository artifactRepository,
                                                            List<ChecksumAlgorithmFactory> checksumAlgorithmFactories )
    {
        boolean enabled = ConfigUtils.getBoolean( session, false, configPropKey( CONF_NAME_ENABLED ) );
        if ( enabled )
        {
            Path baseDir = getBasedir( session );
            if ( baseDir != null && !checksumAlgorithmFactories.isEmpty() )
            {
                Map<String, String> result = performLookup(
                        session, baseDir, artifact, artifactRepository, checksumAlgorithmFactories );

                return result == null || result.isEmpty() ? null : result;
            }
        }
        return null;
    }

    protected abstract Map<String, String> performLookup( RepositorySystemSession session,
                                                          Path baseDir,
                                                          Artifact artifact,
                                                          ArtifactRepository artifactRepository,
                                                          List<ChecksumAlgorithmFactory> checksumAlgorithmFactories );

    protected String configPropKey( String name )
    {
        return CONFIG_PROP_PREFIX + getName() + "." + name;
    }

    private Path getBasedir( RepositorySystemSession session )
    {
        try
        {
            Path basedir = DirectoryUtils.resolveDirectory(
                    session, LOCAL_REPO_PREFIX, configPropKey( "basedir" ), false );
            if ( !Files.isDirectory( basedir ) )
            {
                return null;
            }
            return basedir;
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }
    }
}
