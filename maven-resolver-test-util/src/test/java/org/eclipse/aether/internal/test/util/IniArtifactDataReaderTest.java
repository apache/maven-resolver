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
package org.eclipse.aether.internal.test.util;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 */
public class IniArtifactDataReaderTest {

    private IniArtifactDataReader parser;

    @Before
    public void setup() {
        this.parser = new IniArtifactDataReader("org/eclipse/aether/internal/test/util/");
    }

    @Test
    public void testRelocation() throws IOException {
        String def = "[relocation]\ngid:aid:ext:ver";

        ArtifactDescription description = parser.parseLiteral(def);

        Artifact artifact = description.getRelocation();
        assertNotNull(artifact);
        assertEquals("aid", artifact.getArtifactId());
        assertEquals("gid", artifact.getGroupId());
        assertEquals("ver", artifact.getVersion());
        assertEquals("ext", artifact.getExtension());
    }

    @Test
    public void testDependencies() throws IOException {
        String def = "[dependencies]\ngid:aid:ext:ver\n-exclusion:aid\ngid2:aid2:ext2:ver2";

        ArtifactDescription description = parser.parseLiteral(def);

        List<Dependency> dependencies = description.getDependencies();
        assertNotNull(dependencies);
        assertEquals(2, dependencies.size());

        Dependency dependency = dependencies.get(0);
        assertEquals("compile", dependency.getScope());

        Artifact artifact = dependency.getArtifact();
        assertNotNull(artifact);
        assertEquals("aid", artifact.getArtifactId());
        assertEquals("gid", artifact.getGroupId());
        assertEquals("ver", artifact.getVersion());
        assertEquals("ext", artifact.getExtension());

        Collection<Exclusion> exclusions = dependency.getExclusions();
        assertNotNull(exclusions);
        assertEquals(1, exclusions.size());
        Exclusion exclusion = exclusions.iterator().next();
        assertEquals("exclusion", exclusion.getGroupId());
        assertEquals("aid", exclusion.getArtifactId());
        assertEquals("*", exclusion.getClassifier());
        assertEquals("*", exclusion.getExtension());

        dependency = dependencies.get(1);

        artifact = dependency.getArtifact();
        assertNotNull(artifact);
        assertEquals("aid2", artifact.getArtifactId());
        assertEquals("gid2", artifact.getGroupId());
        assertEquals("ver2", artifact.getVersion());
        assertEquals("ext2", artifact.getExtension());
    }

    @Test
    public void testManagedDependencies() throws IOException {
        String def = "[managed-dependencies]\ngid:aid:ext:ver\n-exclusion:aid\ngid2:aid2:ext2:ver2:runtime";

        ArtifactDescription description = parser.parseLiteral(def);

        List<Dependency> dependencies = description.getManagedDependencies();
        assertNotNull(dependencies);
        assertEquals(2, dependencies.size());

        Dependency dependency = dependencies.get(0);
        assertEquals("", dependency.getScope());

        Artifact artifact = dependency.getArtifact();
        assertNotNull(artifact);
        assertEquals("aid", artifact.getArtifactId());
        assertEquals("gid", artifact.getGroupId());
        assertEquals("ver", artifact.getVersion());
        assertEquals("ext", artifact.getExtension());

        Collection<Exclusion> exclusions = dependency.getExclusions();
        assertNotNull(exclusions);
        assertEquals(1, exclusions.size());
        Exclusion exclusion = exclusions.iterator().next();
        assertEquals("exclusion", exclusion.getGroupId());
        assertEquals("aid", exclusion.getArtifactId());
        assertEquals("*", exclusion.getClassifier());
        assertEquals("*", exclusion.getExtension());

        dependency = dependencies.get(1);
        assertEquals("runtime", dependency.getScope());

        artifact = dependency.getArtifact();
        assertNotNull(artifact);
        assertEquals("aid2", artifact.getArtifactId());
        assertEquals("gid2", artifact.getGroupId());
        assertEquals("ver2", artifact.getVersion());
        assertEquals("ext2", artifact.getExtension());

        assertEquals(0, dependency.getExclusions().size());
    }

    @Test
    public void testResource() throws IOException {
        ArtifactDescription description = parser.parse("ArtifactDataReaderTest.ini");

        Artifact artifact = description.getRelocation();
        assertEquals("gid", artifact.getGroupId());
        assertEquals("aid", artifact.getArtifactId());
        assertEquals("ver", artifact.getVersion());
        assertEquals("ext", artifact.getExtension());

        assertEquals(1, description.getRepositories().size());
        RemoteRepository repo = description.getRepositories().get(0);
        assertEquals("id", repo.getId());
        assertEquals("type", repo.getContentType());
        assertEquals("protocol://some/url?for=testing", repo.getUrl());

        assertDependencies(description.getDependencies());
        assertDependencies(description.getManagedDependencies());
    }

    private void assertDependencies(List<Dependency> deps) {
        assertEquals(4, deps.size());

        Dependency dep = deps.get(0);
        assertEquals("scope", dep.getScope());
        assertFalse(dep.isOptional());
        assertEquals(2, dep.getExclusions().size());
        Iterator<Exclusion> it = dep.getExclusions().iterator();
        Exclusion excl = it.next();
        assertEquals("gid3", excl.getGroupId());
        assertEquals("aid", excl.getArtifactId());
        excl = it.next();
        assertEquals("gid2", excl.getGroupId());
        assertEquals("aid2", excl.getArtifactId());

        Artifact art = dep.getArtifact();
        assertEquals("gid", art.getGroupId());
        assertEquals("aid", art.getArtifactId());
        assertEquals("ver", art.getVersion());
        assertEquals("ext", art.getExtension());

        dep = deps.get(1);
        assertEquals("scope", dep.getScope());
        assertTrue(dep.isOptional());
        assertEquals(0, dep.getExclusions().size());

        art = dep.getArtifact();
        assertEquals("gid", art.getGroupId());
        assertEquals("aid2", art.getArtifactId());
        assertEquals("ver", art.getVersion());
        assertEquals("ext", art.getExtension());

        dep = deps.get(2);
        assertEquals("scope", dep.getScope());
        assertTrue(dep.isOptional());
        assertEquals(0, dep.getExclusions().size());

        art = dep.getArtifact();
        assertEquals("gid", art.getGroupId());
        assertEquals("aid", art.getArtifactId());
        assertEquals("ver3", art.getVersion());
        assertEquals("ext", art.getExtension());

        dep = deps.get(3);
        assertEquals("scope5", dep.getScope());
        assertTrue(dep.isOptional());
        assertEquals(0, dep.getExclusions().size());

        art = dep.getArtifact();
        assertEquals("gid1", art.getGroupId());
        assertEquals("aid", art.getArtifactId());
        assertEquals("ver", art.getVersion());
        assertEquals("ext", art.getExtension());
    }
}
