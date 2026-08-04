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
package org.eclipse.aether.internal.impl;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultLocalPathComposerTest {

    private final DefaultLocalPathComposer composer = new DefaultLocalPathComposer();

    @Test
    void testGetPathForArtifact() {
        String path = composer.getPathForArtifact(new DefaultArtifact("g.id", "a-id", "jar", "1.0"), true);
        assertEquals("g/id/a-id/1.0/a-id-1.0.jar", path);
    }

    @Test
    void testGetPathForMetadata() {
        Metadata metadata = new DefaultMetadata("g.id", "a-id", "1.0", "maven-metadata.xml", Metadata.Nature.RELEASE);
        String path = composer.getPathForMetadata(metadata, "central");
        assertEquals("g/id/a-id/1.0/maven-metadata-central.xml", path);
    }

    @Test
    void testGetPathForMetadataRejectsTraversalInVersion() {
        Metadata metadata = new DefaultMetadata(
                "g.id", "a-id", "../../../../tmp/PWNED-SNAPSHOT", "maven-metadata.xml", Metadata.Nature.RELEASE);
        assertThrows(IllegalArgumentException.class, () -> composer.getPathForMetadata(metadata, "central"));
    }

    @Test
    void testGetPathForArtifactRejectsTraversalInVersion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> composer.getPathForArtifact(new DefaultArtifact("g.id", "a-id", "jar", "../../escape"), true));
    }

    @Test
    void testGetPathForMetadataRejectsSlashInVersion() {
        Metadata metadata =
                new DefaultMetadata("g.id", "a-id", "1.0/../../etc", "maven-metadata.xml", Metadata.Nature.RELEASE);
        assertThrows(IllegalArgumentException.class, () -> composer.getPathForMetadata(metadata, "central"));
    }

    @Test
    void testGetPathForArtifactRejectsBackslashInVersion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> composer.getPathForArtifact(new DefaultArtifact("g.id", "a-id", "jar", "1.0\\..\\escape"), true));
    }

    @Test
    void testGetPathForMetadataAcceptsNormalVersions() {
        // Verify that normal version strings with dots and dashes are still accepted
        Metadata metadata =
                new DefaultMetadata("g.id", "a-id", "1.0.0-SNAPSHOT", "maven-metadata.xml", Metadata.Nature.SNAPSHOT);
        String path = composer.getPathForMetadata(metadata, "central");
        assertEquals("g/id/a-id/1.0.0-SNAPSHOT/maven-metadata-central.xml", path);
    }
}
