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
 * @since 1.9.0
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
     * This implementation will call into underlying code only if enabled, and will enforce non-{@code null} return
     * value. In worst case, empty map should be returned, meaning "no trusted checksums available".
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
        if ( isEnabled( session ) )
        {
            return requireNonNull(
                    doGetTrustedArtifactChecksums( session, artifact, artifactRepository, checksumAlgorithmFactories )
            );
        }
        return null;
    }

    /**
     * This implementation will call into underlying code only if enabled. Underlying implementation may still choose
     * to return {@code null}.
     */
    @Override
    public Writer getTrustedArtifactChecksumsWriter( RepositorySystemSession session )
    {
        requireNonNull( session, "session is null" );
        if ( isEnabled( session ) )
        {
            return doGetTrustedArtifactChecksumsWriter( session );
        }
        return null;
    }

    /**
     * Implementors MUST NOT return {@code null} at this point, as this source is enabled.
     */
    protected abstract Map<String, String> doGetTrustedArtifactChecksums(
            RepositorySystemSession session, Artifact artifact, ArtifactRepository artifactRepository,
            List<ChecksumAlgorithmFactory> checksumAlgorithmFactories );

    /**
     * Implementors may override this method and return {@link Writer} instance.
     */
    protected Writer doGetTrustedArtifactChecksumsWriter( RepositorySystemSession session )
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
     * Returns {@code true} if session configuration marks this instance as enabled.
     * <p>
     * Default value is {@code false}.
     */
    protected boolean isEnabled( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean( session, false, CONFIG_PROP_PREFIX + this.name );
    }

    /**
     * Returns {@code true} if session configuration marks this instance as origin aware.
     * <p>
     * Default value is {@code true}.
     */
    protected boolean isOriginAware( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean( session, true, configPropKey( CONF_NAME_ORIGIN_AWARE ) );
    }

    /**
     * Uses utility {@link DirectoryUtils#resolveDirectory(RepositorySystemSession, String, String, boolean)} to
     * calculate (and maybe create) basedir for this implementation, never returns {@code null}. The returned
     * {@link Path} may not exist, if invoked with {@code mayCreate} being {@code false}.
     * <p>
     * Default value is {@code ${LOCAL_REPOSITORY}/.checksums}.
     *
     * @return The {@link Path} of basedir, never {@code null}.
     */
    protected Path getBasedir( RepositorySystemSession session, boolean mayCreate )
    {
        try
        {
            return DirectoryUtils.resolveDirectory(
                    session, LOCAL_REPO_PREFIX_DIR, configPropKey( CONF_NAME_BASEDIR ), mayCreate );
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }
    }
}
