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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.junit.jupiter.api.Test;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

public class BasedirNameMapperTest extends NameMapperTestSupport {
    private final String PS = "/"; // we work with URIs now, not OS file paths

    BasedirNameMapper mapper = new BasedirNameMapper(new HashingNameMapper(GAVNameMapper.gav()));

    @Test
    void nullsAndEmptyInputs() {
        Collection<String> names;

        names = mapper.nameLocks(session, null, null);
        assertTrue(names.isEmpty());

        names = mapper.nameLocks(session, null, emptyList());
        assertTrue(names.isEmpty());

        names = mapper.nameLocks(session, emptyList(), null);
        assertTrue(names.isEmpty());

        names = mapper.nameLocks(session, emptyList(), emptyList());
        assertTrue(names.isEmpty());
    }

    @Test
    void defaultLocksDir() {
        configProperties.put("aether.syncContext.named.hashing.depth", "0");
        configProperties.put("aether.syncContext.named.basedir.locksDir", null);
        DefaultArtifact artifact = new DefaultArtifact("group:artifact:1.0");
        Collection<String> names = mapper.nameLocks(session, singletonList(artifact), null);
        assertEquals(names.size(), 1);
        assertEquals(
                names.iterator().next(),
                basedir.toUri() + PS + ".locks" + PS + "46e98183d232f1e16f863025080c7f2b9797fd10");
    }

    @Test
    void relativeLocksDir() {
        configProperties.put("aether.syncContext.named.hashing.depth", "0");
        configProperties.put("aether.syncContext.named.basedir.locksDir", "my/locks");
        DefaultArtifact artifact = new DefaultArtifact("group:artifact:1.0");
        Collection<String> names = mapper.nameLocks(session, singletonList(artifact), null);
        assertEquals(names.size(), 1);
        assertEquals(
                names.iterator().next(),
                basedir.toUri() + PS + "my" + PS + "locks" + PS + "46e98183d232f1e16f863025080c7f2b9797fd10");
    }

    @Test
    void absoluteLocksDir() throws IOException {
        String absoluteLocksDir = "/my/locks";
        String customBaseDir =
                new File(absoluteLocksDir).getCanonicalFile().toPath().toUri().toASCIIString();

        configProperties.put("aether.syncContext.named.hashing.depth", "0");
        configProperties.put("aether.syncContext.named.basedir.locksDir", absoluteLocksDir);
        DefaultArtifact artifact = new DefaultArtifact("group:artifact:1.0");
        Collection<String> names = mapper.nameLocks(session, singletonList(artifact), null);
        assertEquals(names.size(), 1);
        assertEquals(names.iterator().next(), customBaseDir + PS + "46e98183d232f1e16f863025080c7f2b9797fd10");
    }

    @Test
    void singleArtifact() {
        configProperties.put("aether.syncContext.named.hashing.depth", "0");

        DefaultArtifact artifact = new DefaultArtifact("group:artifact:1.0");
        Collection<String> names = mapper.nameLocks(session, singletonList(artifact), null);
        assertEquals(names.size(), 1);
        assertEquals(
                names.iterator().next(),
                basedir.toUri() + PS + ".locks" + PS + "46e98183d232f1e16f863025080c7f2b9797fd10");
    }

    @Test
    void singleMetadata() {
        configProperties.put("aether.syncContext.named.hashing.depth", "0");

        DefaultMetadata metadata =
                new DefaultMetadata("group", "artifact", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<String> names = mapper.nameLocks(session, null, singletonList(metadata));
        assertEquals(names.size(), 1);
        assertEquals(
                names.iterator().next(),
                basedir.toUri() + PS + ".locks" + PS + "293b3990971f4b4b02b220620d2538eaac5f221b");
    }

    @Test
    void oneAndOne() {
        configProperties.put("aether.syncContext.named.hashing.depth", "0");

        DefaultArtifact artifact = new DefaultArtifact("agroup:artifact:1.0");
        DefaultMetadata metadata =
                new DefaultMetadata("bgroup", "artifact", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<String> names = mapper.nameLocks(session, singletonList(artifact), singletonList(metadata));

        assertEquals(names.size(), 2);
        Iterator<String> namesIterator = names.iterator();

        // they are sorted as well
        assertEquals(
                namesIterator.next(),
                basedir.toUri() + PS + ".locks" + PS + "d36504431d00d1c6e4d1c34258f2bf0a004de085");
        assertEquals(
                namesIterator.next(),
                basedir.toUri() + PS + ".locks" + PS + "fbcebba60d7eb931eca634f6ca494a8a1701b638");
    }
}
