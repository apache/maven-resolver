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
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

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
                keys.add(NamedLockKey.of(getArtifactName(artifact), ArtifactIdUtils.toBaseId(artifact)));
            }
        }

        if (metadatas != null) {
            for (Metadata metadata : metadatas) {
                keys.add(NamedLockKey.of(getMetadataName(metadata), toMetadataId(metadata)));
            }
        }
        return keys;
    }

    protected String getArtifactName(Artifact artifact) {
        return artifactPrefix
                + fieldToSegment(artifact.getGroupId())
                + fieldSeparator
                + fieldToSegment(artifact.getArtifactId())
                + fieldSeparator
                + fieldToSegment(artifact.getBaseVersion())
                + artifactSuffix;
    }

    /**
     * Returns the given coordinate field in a form that is safe to use as (part of) a file name: when this mapper
     * is {@link #isFileSystemFriendly()}, characters that act as path separators or are otherwise illegal in file
     * names are replaced via {@link PathUtils#stringToPathSegment(String)}. Coordinate fields arrive from remote
     * repository metadata (i.e. from the wire), and file-system friendly names are resolved against the locks
     * base directory by {@link BasedirNameMapper}: without this, a wire-supplied field like {@code "1/../../x"}
     * selects a lock path outside the locks directory, letting a malicious repository create or delete files
     * (including other builds' live locks) at attacker-chosen paths.
     *
     * @since 2.0.23
     */
    protected String fieldToSegment(String field) {
        return fileSystemFriendly ? PathUtils.stringToPathSegment(field) : field;
    }

    protected static final String MAVEN_METADATA = "maven-metadata.xml";

    protected String getMetadataName(Metadata metadata) {
        String name = metadataPrefix;
        if (!metadata.getGroupId().isEmpty()) {
            name += fieldToSegment(metadata.getGroupId());
            if (!metadata.getArtifactId().isEmpty()) {
                name += fieldSeparator + fieldToSegment(metadata.getArtifactId());
                if (!metadata.getVersion().isEmpty()) {
                    name += fieldSeparator + fieldToSegment(metadata.getVersion());
                }
            }
            if (!MAVEN_METADATA.equals(metadata.getType())) {
                name += fieldSeparator + fieldToSegment(metadata.getType());
            }
        } else {
            if (!MAVEN_METADATA.equals(metadata.getType())) {
                name += fieldToSegment(metadata.getType());
            }
        }
        return name + metadataSuffix;
    }

    protected String toMetadataId(Metadata metadata) {
        String name = "";
        if (!metadata.getGroupId().isEmpty()) {
            name += metadata.getGroupId();
            if (!metadata.getArtifactId().isEmpty()) {
                name += ":" + metadata.getArtifactId();
                if (!metadata.getVersion().isEmpty()) {
                    name += ":" + metadata.getVersion();
                }
            }
        }
        if (!metadata.getType().isEmpty()) {
            name += (name.isEmpty() ? "" : ":") + metadata.getType();
        }
        return name;
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
