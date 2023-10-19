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
package org.eclipse.aether.util.artifact;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class ArtifactIdUtilsTest {

    @Test
    public void testToIdArtifact() {
        Artifact artifact = null;
        assertSame(null, ArtifactIdUtils.toId(artifact));

        artifact = new DefaultArtifact("gid", "aid", "ext", "1.0-20110205.132618-23");
        assertEquals("gid:aid:ext:1.0-20110205.132618-23", ArtifactIdUtils.toId(artifact));

        artifact = new DefaultArtifact("gid", "aid", "cls", "ext", "1.0-20110205.132618-23");
        assertEquals("gid:aid:ext:cls:1.0-20110205.132618-23", ArtifactIdUtils.toId(artifact));
    }

    @Test
    public void testToIdStrings() {
        assertEquals(":::", ArtifactIdUtils.toId(null, null, null, null, null));

        assertEquals("gid:aid:ext:1", ArtifactIdUtils.toId("gid", "aid", "ext", "", "1"));

        assertEquals("gid:aid:ext:cls:1", ArtifactIdUtils.toId("gid", "aid", "ext", "cls", "1"));
    }

    @Test
    public void testToBaseIdArtifact() {
        Artifact artifact = null;
        assertSame(null, ArtifactIdUtils.toBaseId(artifact));

        artifact = new DefaultArtifact("gid", "aid", "ext", "1.0-20110205.132618-23");
        assertEquals("gid:aid:ext:1.0-SNAPSHOT", ArtifactIdUtils.toBaseId(artifact));

        artifact = new DefaultArtifact("gid", "aid", "cls", "ext", "1.0-20110205.132618-23");
        assertEquals("gid:aid:ext:cls:1.0-SNAPSHOT", ArtifactIdUtils.toBaseId(artifact));
    }

    @Test
    public void testToVersionlessIdArtifact() {
        Artifact artifact = null;
        assertSame(null, ArtifactIdUtils.toId(artifact));

        artifact = new DefaultArtifact("gid", "aid", "ext", "1");
        assertEquals("gid:aid:ext", ArtifactIdUtils.toVersionlessId(artifact));

        artifact = new DefaultArtifact("gid", "aid", "cls", "ext", "1");
        assertEquals("gid:aid:ext:cls", ArtifactIdUtils.toVersionlessId(artifact));
    }

    @Test
    public void testToVersionlessIdStrings() {
        assertEquals("::", ArtifactIdUtils.toVersionlessId(null, null, null, null));

        assertEquals("gid:aid:ext", ArtifactIdUtils.toVersionlessId("gid", "aid", "ext", ""));

        assertEquals("gid:aid:ext:cls", ArtifactIdUtils.toVersionlessId("gid", "aid", "ext", "cls"));
    }

    @Test
    public void testEqualsId() {
        Artifact artifact1 = null;
        Artifact artifact2 = null;
        assertFalse(ArtifactIdUtils.equalsId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsId(artifact2, artifact1));

        artifact1 = new DefaultArtifact("gid", "aid", "ext", "1.0-20110205.132618-23");
        assertFalse(ArtifactIdUtils.equalsId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gidX", "aid", "ext", "1.0-20110205.132618-23");
        assertFalse(ArtifactIdUtils.equalsId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gid", "aidX", "ext", "1.0-20110205.132618-23");
        assertFalse(ArtifactIdUtils.equalsId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gid", "aid", "extX", "1.0-20110205.132618-23");
        assertFalse(ArtifactIdUtils.equalsId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gid", "aid", "ext", "1.0-20110205.132618-24");
        assertFalse(ArtifactIdUtils.equalsId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gid", "aid", "ext", "1.0-20110205.132618-23");
        assertTrue(ArtifactIdUtils.equalsId(artifact1, artifact2));
        assertTrue(ArtifactIdUtils.equalsId(artifact2, artifact1));

        assertTrue(ArtifactIdUtils.equalsId(artifact1, artifact1));
    }

    @Test
    public void testEqualsBaseId() {
        Artifact artifact1 = null;
        Artifact artifact2 = null;
        assertFalse(ArtifactIdUtils.equalsBaseId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsBaseId(artifact2, artifact1));

        artifact1 = new DefaultArtifact("gid", "aid", "ext", "1.0-20110205.132618-23");
        assertFalse(ArtifactIdUtils.equalsBaseId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsBaseId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gidX", "aid", "ext", "1.0-20110205.132618-23");
        assertFalse(ArtifactIdUtils.equalsBaseId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsBaseId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gid", "aidX", "ext", "1.0-20110205.132618-23");
        assertFalse(ArtifactIdUtils.equalsBaseId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsBaseId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gid", "aid", "extX", "1.0-20110205.132618-23");
        assertFalse(ArtifactIdUtils.equalsBaseId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsBaseId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gid", "aid", "ext", "X.0-20110205.132618-23");
        assertFalse(ArtifactIdUtils.equalsBaseId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsBaseId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gid", "aid", "ext", "1.0-20110205.132618-24");
        assertTrue(ArtifactIdUtils.equalsBaseId(artifact1, artifact2));
        assertTrue(ArtifactIdUtils.equalsBaseId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gid", "aid", "ext", "1.0-20110205.132618-23");
        assertTrue(ArtifactIdUtils.equalsBaseId(artifact1, artifact2));
        assertTrue(ArtifactIdUtils.equalsBaseId(artifact2, artifact1));

        assertTrue(ArtifactIdUtils.equalsBaseId(artifact1, artifact1));
    }

    @Test
    public void testEqualsVersionlessId() {
        Artifact artifact1 = null;
        Artifact artifact2 = null;
        assertFalse(ArtifactIdUtils.equalsVersionlessId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsVersionlessId(artifact2, artifact1));

        artifact1 = new DefaultArtifact("gid", "aid", "ext", "1.0-20110205.132618-23");
        assertFalse(ArtifactIdUtils.equalsVersionlessId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsVersionlessId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gidX", "aid", "ext", "1.0-20110205.132618-23");
        assertFalse(ArtifactIdUtils.equalsVersionlessId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsVersionlessId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gid", "aidX", "ext", "1.0-20110205.132618-23");
        assertFalse(ArtifactIdUtils.equalsVersionlessId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsVersionlessId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gid", "aid", "extX", "1.0-20110205.132618-23");
        assertFalse(ArtifactIdUtils.equalsVersionlessId(artifact1, artifact2));
        assertFalse(ArtifactIdUtils.equalsVersionlessId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gid", "aid", "ext", "1.0-20110205.132618-24");
        assertTrue(ArtifactIdUtils.equalsVersionlessId(artifact1, artifact2));
        assertTrue(ArtifactIdUtils.equalsVersionlessId(artifact2, artifact1));

        artifact2 = new DefaultArtifact("gid", "aid", "ext", "1.0-20110205.132618-23");
        assertTrue(ArtifactIdUtils.equalsVersionlessId(artifact1, artifact2));
        assertTrue(ArtifactIdUtils.equalsVersionlessId(artifact2, artifact1));

        assertTrue(ArtifactIdUtils.equalsVersionlessId(artifact1, artifact1));
    }
}
