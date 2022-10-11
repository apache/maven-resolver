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
import java.util.Collections;
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
 * Support class for implementing {@link TrustedChecksumsSource} backed by local filesystem. It implements basic support
 * like basedir calculation, "enabled" flag and "originAware" flag.
 * <p>
 * The configuration keys supported:
 * <ul>
 *     <li><pre>aether.trustedChecksumsSource.${name}</pre> (boolean) must be explicitly set to "true"
 *     to become enabled</li>
 *     <li><pre>aether.trustedChecksumsSource.${name}.basedir</pre> (string, path) directory from where implementation
 *     can use files. May be relative path (then is resolved against local repository basedir) or absolute. If unset,
 *     default value is ".checksums" and is resolved against local repository basedir.</li>
 *     <li><pre>aether.trustedChecksumsSource.${name}.originAware</pre> (boolean) whether to make implementation
 *     "originAware", to factor in origin repository ID as well or not.</li>
 * </ul>
 * <p>
 * This implementation ensures that implementations have "name" property, used in configuration properties above.
 *
 * @since TBD
 */
abstract class FileTrustedChecksumsSourceSupport
        implements TrustedChecksumsSource
{
    private static final String CONFIG_PROP_PREFIX = "aether.trustedChecksumsSource.";

    private static final String CONF_NAME_BASEDIR = "basedir";

    private static final String CONF_NAME_ORIGIN_AWARE = "originAware";

    /**
     * Visible for testing.
     */
    static final String LOCAL_REPO_PREFIX_DIR = ".checksums";

    private final String name;

    FileTrustedChecksumsSourceSupport( String name )
    {
        this.name = requireNonNull( name );
    }

    /**
     * The implementation will call into underlying code only if enabled, chosen basedir exists, and requested
     * checksum algorithms are not empty.
     */
    @Override
    public Map<String, String> getTrustedArtifactChecksums( RepositorySystemSession session,
                                                            Artifact artifact,
                                                            ArtifactRepository artifactRepository,
                                                            List<ChecksumAlgorithmFactory> checksumAlgorithmFactories )
    {
        requireNonNull( session, "session is null" );
        requireNonNull( artifact, "artifact is null" );
        requireNonNull( artifactRepository, "artifactRepository is null" );
        requireNonNull( checksumAlgorithmFactories, "checksumAlgorithmFactories is null" );
        boolean enabled = ConfigUtils.getBoolean( session, false, CONFIG_PROP_PREFIX + this.name );
        if ( enabled )
        {
            Path basedir = getBasedir( session, false );
            if ( basedir != null && !checksumAlgorithmFactories.isEmpty() )
            {
                return requireNonNull(
                        performLookup( session, basedir, artifact, artifactRepository, checksumAlgorithmFactories )
                );
            }
            else
            {
                return Collections.emptyMap();
            }
        }
        return null;
    }

    @Override
    public Writer getTrustedArtifactChecksumsWriter( RepositorySystemSession session )
    {
        requireNonNull( session, "session is null" );
        boolean enabled = ConfigUtils.getBoolean( session, false, CONFIG_PROP_PREFIX + this.name );
        if ( enabled )
        {
            return getWriter( session, getBasedir( session, true ) );
        }
        return null;
    }

    /**
     * Implementors MUST NOT return {@code null} at this point, as the "source is enabled" check was already performed
     * and IS enabled, worst can happen is checksums for asked artifact are not available.
     */
    protected abstract Map<String, String> performLookup( RepositorySystemSession session,
                                                          Path basedir,
                                                          Artifact artifact,
                                                          ArtifactRepository artifactRepository,
                                                          List<ChecksumAlgorithmFactory> checksumAlgorithmFactories );

    /**
     * If a subclass of this support class support
     * {@link org.eclipse.aether.spi.checksums.TrustedChecksumsSource.Writer}, or in other words "is writable", it
     * should override this method and return proper instance.
     */
    protected Writer getWriter( RepositorySystemSession session, Path basedir )
    {
        return null;
    }

    /**
     * To be used by underlying implementations to form configuration property keys properly scoped.
     */
    protected String configPropKey( String name )
    {
        requireNonNull( name );
        return CONFIG_PROP_PREFIX + this.name + "." + name;
    }

    /**
     * Returns {@code true} if session configuration contains "originAware" property set to {@code true}.
     */
    protected boolean isOriginAware( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean( session, false, configPropKey( CONF_NAME_ORIGIN_AWARE ) );
    }

    /**
     * Uses common {@link DirectoryUtils} to calculate (and maybe create) basedir for this implementation. Returns
     * {@code null} if the calculated basedir does not exist and {@code mayCreate} was {@code false}. If
     * {@code mayCreate} parameter was {@code true}, this method always returns non-null {@link Path} or throws.
     */
    private Path getBasedir( RepositorySystemSession session, boolean mayCreate )
    {
        try
        {
            Path basedir = DirectoryUtils.resolveDirectory(
                    session, LOCAL_REPO_PREFIX_DIR, configPropKey( CONF_NAME_BASEDIR ), mayCreate );
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
