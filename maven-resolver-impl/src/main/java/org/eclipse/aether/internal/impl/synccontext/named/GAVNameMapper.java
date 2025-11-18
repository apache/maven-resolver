/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.internal.impl.synccontext.named;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.named.NamedLockKey;
import org.eclipse.aether.util.PathUtils;

import static java.util.Objects.requireNonNull;

/**
 * Artifact GAV {@link NameMapper}, uses artifact and metadata coordinates to name their corresponding locks. Is not
 * considering local repository, only the artifact coordinates. May use custom prefixes and suffixes and separators,
 * hence this instance may or may not be filesystem friendly (depends on strings used).
 * <p>
 * Note: in earlier Resolver 1.9.x versions this mapper was the default, but it changed to {@link GAECVNameMapper}
 * in 1.9.25.
 */
public class GAVNameMapper implements NameMapper {
    protected final boolean fileSystemFriendly;

    protected final String artifactPrefix;

    protected final String artifactSuffix;

    protected final String metadataPrefix;

    protected final String metadataSuffix;

    protected final String fieldSeparator;

    public GAVNameMapper(
            boolean fileSystemFriendly,
            String artifactPrefix,
            String artifactSuffix,
            String metadataPrefix,
            String metadataSuffix,
            String fieldSeparator) {
        this.fileSystemFriendly = fileSystemFriendly;
        this.artifactPrefix = requireNonNull(artifactPrefix);
        this.artifactSuffix = requireNonNull(artifactSuffix);
        this.metadataPrefix = requireNonNull(metadataPrefix);
        this.metadataSuffix = requireNonNull(metadataSuffix);
        this.fieldSeparator = requireNonNull(fieldSeparator);
    }

    @Override
    public boolean isFileSystemFriendly() {
        return fileSystemFriendly;
    }

    @Override
    public Collection<NamedLockKey> nameLocks(
            final RepositorySystemSession session,
            final Collection<? extends Artifact> artifacts,
            final Collection<? extends Metadata> metadatas) {
        // Deadlock prevention: https://stackoverflow.com/a/16780988/696632
        // We must acquire multiple locks always in the same order!
        TreeSet<NamedLockKey> keys = new TreeSet<>(Comparator.comparing(NamedLockKey::name));
        if (artifacts != null) {
            for (Artifact artifact : artifacts) {
                keys.add(NamedLockKey.of(
                        getArtifactName(artifact, artifactPrefix, fieldSeparator, artifactSuffix),
                        getArtifactName(artifact, "", ":", "")));
            }
        }

        if (metadatas != null) {
            for (Metadata metadata : metadatas) {
                keys.add(NamedLockKey.of(
                        getMetadataName(metadata, metadataPrefix, fieldSeparator, metadataSuffix),
                        getMetadataName(metadata, "", ":", "")));
            }
        }
        return keys;
    }

    protected String getArtifactName(Artifact artifact, String prefix, String separator, String suffix) {
        return prefix
                + artifact.getGroupId()
                + separator
                + artifact.getArtifactId()
                + separator
                + artifact.getBaseVersion()
                + suffix;
    }

    protected static final String MAVEN_METADATA = "maven-metadata.xml";

    protected String getMetadataName(Metadata metadata, String prefix, String separator, String suffix) {
        String name = prefix;
        if (!metadata.getGroupId().isEmpty()) {
            name += metadata.getGroupId();
            if (!metadata.getArtifactId().isEmpty()) {
                name += separator + metadata.getArtifactId();
                if (!metadata.getVersion().isEmpty()) {
                    name += separator + metadata.getVersion();
                }
            }
            if (!MAVEN_METADATA.equals(metadata.getType())) {
                name += separator + PathUtils.stringToPathSegment(metadata.getType());
            }
        } else {
            if (!MAVEN_METADATA.equals(metadata.getType())) {
                name += PathUtils.stringToPathSegment(metadata.getType());
            }
        }
        return name + suffix;
    }

    /**
     * @deprecated Use {@link NameMappers} to create name mappers instead.
     */
    @Deprecated
    public static NameMapper gav() {
        return new GAVNameMapper(false, "artifact:", "", "metadata:", "", ":");
    }

    /**
     * @deprecated Use {@link NameMappers} to create name mappers instead.
     */
    @Deprecated
    public static NameMapper fileGav() {
        return new GAVNameMapper(true, "artifact~", ".lock", "metadata~", ".lock", "~");
    }
}
