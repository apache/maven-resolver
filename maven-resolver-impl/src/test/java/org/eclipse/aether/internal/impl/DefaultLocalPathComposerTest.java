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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DefaultLocalPathComposerTest {
    private final DefaultLocalPathComposer composer = new DefaultLocalPathComposer();

    @Test
    public void testGetPathForArtifactAcceptsNormalCoordinates() {
        String path =
                composer.getPathForArtifact(new DefaultArtifact("org.apache.maven", "commons-io", "jar", "1.0"), true);
        assertEquals("org/apache/maven/commons-io/1.0/commons-io-1.0.jar", path);
    }

    @Test
    public void testGetPathForArtifactRejectsGroupIdWithEmptyDotSegments() {
        assertRejected(new DefaultArtifact(".leading", "a-id", "jar", "1.0"));
        assertRejected(new DefaultArtifact("..leading", "a-id", "jar", "1.0"));
        assertRejected(new DefaultArtifact("g..id", "a-id", "jar", "1.0"));
        assertRejected(new DefaultArtifact("g.id.", "a-id", "jar", "1.0"));
    }

    @Test
    public void testGetPathForArtifactRejectsColon() {
        assertRejected(new DefaultArtifact("g:id", "a-id", "jar", "1.0"));
        assertRejected(new DefaultArtifact("g.id", "a-id", "jar", "1.0:1"));
    }

    @Test
    public void testGetPathForMetadataAcceptsNormalCoordinates() {
        Metadata metadata =
                new DefaultMetadata("g.id", "a-id", "1.0.0-SNAPSHOT", "maven-metadata.xml", Metadata.Nature.SNAPSHOT);
        String path = composer.getPathForMetadata(metadata, "central");
        assertEquals("g/id/a-id/1.0.0-SNAPSHOT/maven-metadata-central.xml", path);
    }

    @Test
    public void testGetPathForMetadataRejectsGroupIdWithEmptyDotSegments() {
        Metadata metadata =
                new DefaultMetadata(".leading", "a-id", "1.0", "maven-metadata.xml", Metadata.Nature.RELEASE);
        try {
            composer.getPathForMetadata(metadata, "central");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    private void assertRejected(Artifact artifact) {
        try {
            composer.getPathForArtifact(artifact, true);
            fail("expected IllegalArgumentException for " + artifact);
        } catch (IllegalArgumentException expected) {
        }
    }
}
