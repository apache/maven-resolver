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
import java.util.Iterator;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.named.NamedLockKey;
import org.junit.jupiter.api.Test;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GAVNameMapperTest extends NameMapperTestSupport {
    NameMapper mapper = GAVNameMapper.fileGav();

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
    void singleArtifact() {
        DefaultArtifact artifact = new DefaultArtifact("group:artifact:1.0");
        Collection<NamedLockKey> names = mapper.nameLocks(session, singletonList(artifact), null);
        assertEquals(1, names.size());
        assertEquals("artifact~group~artifact~1.0.lock", names.iterator().next().name());
    }

    @Test
    void singleMetadata() {
        DefaultMetadata metadata =
                new DefaultMetadata("group", "artifact", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<NamedLockKey> names = mapper.nameLocks(session, null, singletonList(metadata));
        assertEquals(1, names.size());
        assertEquals("metadata~group~artifact.lock", names.iterator().next().name());
    }

    @Test
    void prefixMetadata() {
        DefaultMetadata metadata =
                new DefaultMetadata("", "", ".meta/prefixes-central.txt", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<NamedLockKey> names = mapper.nameLocks(session, null, singletonList(metadata));
        assertEquals(1, names.size());
        assertEquals(
                "metadata~.meta-SLASH-prefixes-central.txt.lock",
                names.iterator().next().name());
    }

    @Test
    void rootSomeMetadata() {
        DefaultMetadata metadata = new DefaultMetadata("", "", "something.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<NamedLockKey> names = mapper.nameLocks(session, null, singletonList(metadata));
        assertEquals(1, names.size());
        assertEquals("metadata~something.xml.lock", names.iterator().next().name());
    }

    @Test
    void nonRootSomeMetadata() {
        DefaultMetadata metadata =
                new DefaultMetadata("groupId", "artifactId", "something.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<NamedLockKey> names = mapper.nameLocks(session, null, singletonList(metadata));
        assertEquals(1, names.size());
        assertEquals(
                "metadata~groupId~artifactId~something.xml.lock",
                names.iterator().next().name());
    }

    @Test
    void oneAndOne() {
        DefaultArtifact artifact = new DefaultArtifact("agroup:artifact:1.0");
        DefaultMetadata metadata =
                new DefaultMetadata("bgroup", "artifact", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<NamedLockKey> names = mapper.nameLocks(session, singletonList(artifact), singletonList(metadata));

        assertEquals(2, names.size());
        Iterator<NamedLockKey> namesIterator = names.iterator();

        // they are sorted as well
        assertEquals("artifact~agroup~artifact~1.0.lock", namesIterator.next().name());
        assertEquals("metadata~bgroup~artifact.lock", namesIterator.next().name());
    }
}
