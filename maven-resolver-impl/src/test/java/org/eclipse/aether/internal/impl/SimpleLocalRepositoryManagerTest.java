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

import java.io.File;
import java.io.IOException;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SimpleLocalRepositoryManagerTest {

    @TempDir
    private File basedir;

    private SimpleLocalRepositoryManager manager;

    private RepositorySystemSession session;

    @BeforeEach
    void setup() throws IOException {
        manager = new SimpleLocalRepositoryManager(basedir.toPath(), "simple", new DefaultLocalPathComposer());
        session = TestUtils.newSession();
    }

    @Test
    void testGetPathForLocalArtifact() {
        Artifact artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-SNAPSHOT");
        assertEquals("1.0-SNAPSHOT", artifact.getBaseVersion());
        assertEquals("g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-SNAPSHOT.jar", manager.getPathForLocalArtifact(artifact));

        artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-20110329.221805-4");
        assertEquals("1.0-SNAPSHOT", artifact.getBaseVersion());
        assertEquals("g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-SNAPSHOT.jar", manager.getPathForLocalArtifact(artifact));

        artifact = new DefaultArtifact("g.i.d", "a.i.d", "", "", "1.0-SNAPSHOT");
        assertEquals("g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-SNAPSHOT", manager.getPathForLocalArtifact(artifact));
    }

    @Test
    void testGetPathForRemoteArtifact() {
        RemoteRepository remoteRepo = new RemoteRepository.Builder("repo", "default", "ram:/void").build();

        Artifact artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-SNAPSHOT");
        assertEquals("1.0-SNAPSHOT", artifact.getBaseVersion());
        assertEquals(
                "g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-SNAPSHOT.jar",
                manager.getPathForRemoteArtifact(artifact, remoteRepo, ""));

        artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-20110329.221805-4");
        assertEquals("1.0-SNAPSHOT", artifact.getBaseVersion());
        assertEquals(
                "g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-20110329.221805-4.jar",
                manager.getPathForRemoteArtifact(artifact, remoteRepo, ""));
    }

    @Test
    void testFindArtifactUsesTimestampedVersion() throws Exception {
        Artifact artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-SNAPSHOT");
        File file = new File(basedir, manager.getPathForLocalArtifact(artifact));
        TestFileUtils.writeString(file, "test");

        artifact = artifact.setVersion("1.0-20110329.221805-4");
        LocalArtifactRequest request = new LocalArtifactRequest();
        request.setArtifact(artifact);
        LocalArtifactResult result = manager.find(session, request);
        assertNull(result.getFile(), result.toString());
        assertFalse(result.isAvailable(), result.toString());
    }
}
