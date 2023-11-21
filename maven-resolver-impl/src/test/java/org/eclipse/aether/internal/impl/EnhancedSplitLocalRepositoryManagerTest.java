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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EnhancedSplitLocalRepositoryManagerTest extends EnhancedLocalRepositoryManagerTest {

    @Override
    protected EnhancedLocalRepositoryManager getManager() {
        session.setConfigProperty(DefaultLocalPathPrefixComposerFactory.CONF_PROP_SPLIT, Boolean.TRUE.toString());
        return new EnhancedLocalRepositoryManager(
                basedir,
                new DefaultLocalPathComposer(),
                "_remote.repositories",
                trackingFileManager,
                new DefaultLocalPathPrefixComposerFactory().createComposer(session));
    }

    @Test
    @Override
    public void testGetPathForLocalArtifact() {
        Artifact artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-SNAPSHOT");
        assertEquals("1.0-SNAPSHOT", artifact.getBaseVersion());
        assertEquals(
                "installed/g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-SNAPSHOT.jar", manager.getPathForLocalArtifact(artifact));

        artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-20110329.221805-4");
        assertEquals("1.0-SNAPSHOT", artifact.getBaseVersion());
        assertEquals(
                "installed/g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-SNAPSHOT.jar", manager.getPathForLocalArtifact(artifact));
    }

    @Test
    @Override
    public void testGetPathForRemoteArtifact() {
        RemoteRepository remoteRepo = new RemoteRepository.Builder("repo", "default", "ram:/void").build();

        Artifact artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-SNAPSHOT");
        assertEquals("1.0-SNAPSHOT", artifact.getBaseVersion());
        assertEquals(
                "cached/g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-SNAPSHOT.jar",
                manager.getPathForRemoteArtifact(artifact, remoteRepo, ""));

        artifact = new DefaultArtifact("g.i.d:a.i.d:1.0-20110329.221805-4");
        assertEquals("1.0-SNAPSHOT", artifact.getBaseVersion());
        assertEquals(
                "cached/g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-20110329.221805-4.jar",
                manager.getPathForRemoteArtifact(artifact, remoteRepo, ""));
    }
}
