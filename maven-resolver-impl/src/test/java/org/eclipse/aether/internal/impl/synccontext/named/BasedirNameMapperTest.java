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
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.named.NamedLockKey;
import org.eclipse.aether.util.DirectoryUtils;
import org.junit.jupiter.api.Test;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

public class BasedirNameMapperTest extends NameMapperTestSupport {
    private final BasedirNameMapper mapper = new BasedirNameMapper(GAVNameMapper.fileGav());

    private String getPrefix() throws IOException {
        Path basedir = DirectoryUtils.resolveDirectory(
                session, BasedirNameMapper.DEFAULT_LOCKS_DIR, BasedirNameMapper.CONFIG_PROP_LOCKS_DIR, false);
        String basedirPath = basedir.toAbsolutePath().toUri().toASCIIString();
        if (!basedirPath.endsWith("/")) {
            basedirPath = basedirPath + "/";
        }
        return basedirPath;
    }

    @Test
    void nullsAndEmptyInputs() {
        Collection<NamedLockKey> names;

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
    void defaultLocksDir() throws IOException {
        configProperties.put("aether.syncContext.named.hashing.depth", "0");
        configProperties.put("aether.syncContext.named.basedir.locksDir", null);
        DefaultArtifact artifact = new DefaultArtifact("group:artifact:1.0");
        Collection<NamedLockKey> names = mapper.nameLocks(session, singletonList(artifact), null);
        assertEquals(1, names.size());
        assertEquals(
                getPrefix() + "artifact~group~artifact~1.0.lock",
                names.iterator().next().name());
    }

    @Test
    void relativeLocksDir() throws IOException {
        configProperties.put("aether.syncContext.named.hashing.depth", "0");
        configProperties.put("aether.syncContext.named.basedir.locksDir", "my/locks");
        DefaultArtifact artifact = new DefaultArtifact("group:artifact:1.0");
        Collection<NamedLockKey> names = mapper.nameLocks(session, singletonList(artifact), null);
        assertEquals(1, names.size());
        assertEquals(
                getPrefix() + "artifact~group~artifact~1.0.lock",
                names.iterator().next().name());
    }

    @Test
    void absoluteLocksDir() throws IOException {
        String absoluteLocksDir = "/my/locks";
        String customBaseDir =
                new File(absoluteLocksDir).getCanonicalFile().toPath().toUri().toASCIIString();

        configProperties.put("aether.syncContext.named.hashing.depth", "0");
        configProperties.put("aether.syncContext.named.basedir.locksDir", absoluteLocksDir);
        DefaultArtifact artifact = new DefaultArtifact("group:artifact:1.0");
        Collection<NamedLockKey> names = mapper.nameLocks(session, singletonList(artifact), null);
        assertEquals(1, names.size());
        assertEquals(
                getPrefix() + "artifact~group~artifact~1.0.lock",
                names.iterator().next().name());
    }

    @Test
    void singleArtifact() throws IOException {
        configProperties.put("aether.syncContext.named.hashing.depth", "0");

        DefaultArtifact artifact = new DefaultArtifact("group:artifact:1.0");
        Collection<NamedLockKey> names = mapper.nameLocks(session, singletonList(artifact), null);
        assertEquals(1, names.size());
        assertEquals(
                getPrefix() + "artifact~group~artifact~1.0.lock",
                names.iterator().next().name());
    }

    @Test
    void singleMetadata() throws IOException {
        configProperties.put("aether.syncContext.named.hashing.depth", "0");

        DefaultMetadata metadata =
                new DefaultMetadata("group", "artifact", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<NamedLockKey> names = mapper.nameLocks(session, null, singletonList(metadata));
        assertEquals(1, names.size());
        assertEquals(
                getPrefix() + "metadata~group~artifact.lock",
                names.iterator().next().name());
    }

    @Test
    void prefixMetadata() throws IOException {
        configProperties.put("aether.syncContext.named.hashing.depth", "0");

        DefaultMetadata metadata =
                new DefaultMetadata("", "", ".meta/prefixes-central.txt", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<NamedLockKey> names = mapper.nameLocks(session, null, singletonList(metadata));
        assertEquals(1, names.size());
        assertEquals(
                getPrefix() + "metadata~.meta_prefixes-central.txt.lock",
                names.iterator().next().name());
    }

    @Test
    void rootSomeMetadata() throws IOException {
        configProperties.put("aether.syncContext.named.hashing.depth", "0");

        DefaultMetadata metadata = new DefaultMetadata("", "", "something.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<NamedLockKey> names = mapper.nameLocks(session, null, singletonList(metadata));
        assertEquals(1, names.size());
        assertEquals(
                getPrefix() + "metadata~something.xml.lock",
                names.iterator().next().name());
    }

    @Test
    void nonRootSomeMetadata() throws IOException {
        configProperties.put("aether.syncContext.named.hashing.depth", "0");

        DefaultMetadata metadata =
                new DefaultMetadata("groupId", "artifactId", "something.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<NamedLockKey> names = mapper.nameLocks(session, null, singletonList(metadata));
        assertEquals(1, names.size());
        assertEquals(
                getPrefix() + "metadata~groupId~artifactId~something.xml.lock",
                names.iterator().next().name());
    }

    @Test
    void oneAndOne() throws IOException {
        configProperties.put("aether.syncContext.named.hashing.depth", "0");

        DefaultArtifact artifact = new DefaultArtifact("agroup:artifact:1.0");
        DefaultMetadata metadata =
                new DefaultMetadata("bgroup", "artifact", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<NamedLockKey> names = mapper.nameLocks(session, singletonList(artifact), singletonList(metadata));

        assertEquals(2, names.size());
        Iterator<NamedLockKey> namesIterator = names.iterator();

        // they are sorted as well
        assertEquals(
                getPrefix() + "artifact~agroup~artifact~1.0.lock",
                namesIterator.next().name());
        assertEquals(
                getPrefix() + "metadata~bgroup~artifact.lock",
                namesIterator.next().name());
    }
}
