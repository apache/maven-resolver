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

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.checksum.ArtifactChecksumFilter;
import org.eclipse.aether.spi.connector.checksum.ArtifactChecksumFilterFactory;
import org.eclipse.aether.util.ConfigUtils;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation that implements default resolver strategy: filters by known (user configured) extensions,
 * or by default only for GPG signature.
 *
 * @since 1.8.0
 */
@Singleton
@Named
public class DefaultArtifactChecksumFilterFactory
        implements ArtifactChecksumFilterFactory
{
    public static final String CONFIG_PROP_OMIT_CHECKSUMS_FOR_EXTENSIONS =
            "aether.checksums.omitChecksumsForExtensions";

    private static final String DEFAULT_OMIT_CHECKSUMS_FOR_EXTENSIONS = ".asc";

    @Override
    public ArtifactChecksumFilter newInstance( RepositorySystemSession session, RemoteRepository repository )
    {
        // ensure uniqueness of (potentially user set) extension list
        Set<String> omitChecksumsForExtensions = Arrays.stream( ConfigUtils.getString(
                session, DEFAULT_OMIT_CHECKSUMS_FOR_EXTENSIONS, CONFIG_PROP_OMIT_CHECKSUMS_FOR_EXTENSIONS )
                .split( "," )
        ).filter( s -> s != null && !s.trim().isEmpty() ).collect( Collectors.toSet() );

        return new ExtensionArtifactChecksumFilter( omitChecksumsForExtensions );
    }

    private static class ExtensionArtifactChecksumFilter
            implements ArtifactChecksumFilter
    {
        private final Set<String> extensionsWithoutChecksums;

        private ExtensionArtifactChecksumFilter( Set<String> extensionsWithoutChecksums )
        {
            this.extensionsWithoutChecksums = requireNonNull( extensionsWithoutChecksums );
        }

        @Override
        public boolean omitChecksumsFor( Artifact artifact )
        {
            String artifactExtension = artifact.getExtension(); // ie. pom.asc
            for ( String extensionWithoutChecksums : extensionsWithoutChecksums )
            {
                if ( artifactExtension.endsWith( extensionWithoutChecksums ) )
                {
                    return true;
                }
            }
            return false;
        }
    }
}
