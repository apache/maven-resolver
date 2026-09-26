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
package org.eclipse.aether.internal.impl.collect;

import java.util.Collections;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DataPoolTest {

    private DataPool newDataPool() {
        return new DataPool(TestUtils.newSession());
    }

    @Test
    void testArtifactDescriptorCaching() {
        ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();
        request.setArtifact(new DefaultArtifact("gid:aid:1"));
        ArtifactDescriptorResult result = new ArtifactDescriptorResult(request);
        result.setArtifact(new DefaultArtifact("gid:aid:2"));
        result.addRelocation(request.getArtifact());
        result.addDependency(new Dependency(new DefaultArtifact("gid:dep:3"), "compile"));
        result.addManagedDependency(new Dependency(new DefaultArtifact("gid:mdep:3"), "runtime"));
        result.addRepository(new RemoteRepository.Builder("test", "default", "http://localhost").build());
        result.addAlias(new DefaultArtifact("gid:alias:4"));

        DataPool pool = newDataPool();
        DataPool.DescriptorKey key = pool.toKey(request);
        pool.putDescriptor(key, result);
        ArtifactDescriptorResult cached = pool.getDescriptor(key, request);
        assertNotNull(cached);
        assertEquals(result.getArtifact(), cached.getArtifact());
        assertEquals(result.getRelocations(), cached.getRelocations());
        assertEquals(result.getDependencies(), cached.getDependencies());
        assertEquals(result.getManagedDependencies(), cached.getManagedDependencies());
        assertEquals(result.getRepositories(), cached.getRepositories());
        assertEquals(result.getAliases(), cached.getAliases());
    }

    @Test
    void testDescriptorCachingIsRepositoryScoped() {
        RemoteRepository repoA =
                new RemoteRepository.Builder("repo-a", "default", "https://repo-a.example.com/").build();
        RemoteRepository repoB =
                new RemoteRepository.Builder("repo-b", "default", "https://repo-b.example.com/").build();

        ArtifactDescriptorRequest requestA = new ArtifactDescriptorRequest();
        requestA.setArtifact(new DefaultArtifact("gid:aid:1"));
        requestA.setRepositories(Collections.singletonList(repoA));
        ArtifactDescriptorResult result = new ArtifactDescriptorResult(requestA);
        result.setArtifact(requestA.getArtifact());

        DataPool pool = newDataPool();
        pool.putDescriptor(pool.toKey(requestA), result);

        // same artifact, same repositories (equal id + url): must hit
        ArtifactDescriptorRequest sameAsA = new ArtifactDescriptorRequest();
        sameAsA.setArtifact(new DefaultArtifact("gid:aid:1"));
        sameAsA.setRepositories(Collections.singletonList(
                new RemoteRepository.Builder("repo-a", "default", "https://repo-a.example.com/").build()));
        assertNotNull(pool.getDescriptor(pool.toKey(sameAsA), sameAsA));

        // same artifact, different repositories: must NOT replay the cached descriptor
        ArtifactDescriptorRequest requestB = new ArtifactDescriptorRequest();
        requestB.setArtifact(new DefaultArtifact("gid:aid:1"));
        requestB.setRepositories(Collections.singletonList(repoB));
        assertNull(pool.getDescriptor(pool.toKey(requestB), requestB));
    }

    @Test
    void testBadDescriptorCachingIsRepositoryScoped() {
        RemoteRepository repoA =
                new RemoteRepository.Builder("repo-a", "default", "https://repo-a.example.com/").build();
        RemoteRepository repoB =
                new RemoteRepository.Builder("repo-b", "default", "https://repo-b.example.com/").build();

        ArtifactDescriptorRequest requestA = new ArtifactDescriptorRequest();
        requestA.setArtifact(new DefaultArtifact("gid:aid:1"));
        requestA.setRepositories(Collections.singletonList(repoA));

        DataPool pool = newDataPool();
        DataPool.DescriptorKey keyA = pool.toKey(requestA);
        pool.putDescriptor(
                keyA, new ArtifactDescriptorException(new ArtifactDescriptorResult(requestA), "descriptor is bad"));

        // same artifact, same repositories: cached failure replays (as the sentinel) and carries its reason
        ArtifactDescriptorRequest sameAsA = new ArtifactDescriptorRequest();
        sameAsA.setArtifact(new DefaultArtifact("gid:aid:1"));
        sameAsA.setRepositories(Collections.singletonList(
                new RemoteRepository.Builder("repo-a", "default", "https://repo-a.example.com/").build()));
        assertSame(DataPool.NO_DESCRIPTOR, pool.getDescriptor(pool.toKey(sameAsA), sameAsA));
        assertEquals("descriptor is bad", pool.getDescriptorFailure(pool.toKey(sameAsA)));

        // same artifact, different repositories: the failure must NOT be replayed cross-context
        ArtifactDescriptorRequest requestB = new ArtifactDescriptorRequest();
        requestB.setArtifact(new DefaultArtifact("gid:aid:1"));
        requestB.setRepositories(Collections.singletonList(repoB));
        assertNull(pool.getDescriptor(pool.toKey(requestB), requestB));
        assertNull(pool.getDescriptorFailure(pool.toKey(requestB)));
    }

    @Test
    void testConstraintKey() {
        VersionRangeRequest request = new VersionRangeRequest();
        request.setRepositories(Collections.singletonList(
                new RemoteRepository.Builder("some-id", "some-type", "http://www.example.com").build()));
        request.setArtifact(new DefaultArtifact("group:artifact:1.0"));

        DataPool pool = newDataPool();

        Object key1 = pool.toKey(request);
        Object key2 = pool.toKey(request);
        assertEquals(key1, key2);
    }
}
