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
import org.hamcrest.Matchers;
import org.junit.Test;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class GAECVNameMapperTest extends NameMapperTestSupport {
    NameMapper mapper = NameMappers.gaecvNameMapper(true);

    @Test
    public void nullsAndEmptyInputs() {
        Collection<String> names;

        names = mapper.nameLocks(session, null, null);
        assertThat(names, Matchers.empty());

        names = mapper.nameLocks(session, null, emptyList());
        assertThat(names, Matchers.empty());

        names = mapper.nameLocks(session, emptyList(), null);
        assertThat(names, Matchers.empty());

        names = mapper.nameLocks(session, emptyList(), emptyList());
        assertThat(names, Matchers.empty());
    }

    @Test
    public void singleArtifactWithoutClassifier() {
        DefaultArtifact artifact = new DefaultArtifact("group:artifact:1.0");
        Collection<String> names = mapper.nameLocks(session, singletonList(artifact), null);

        assertThat(names, hasSize(1));
        assertThat(names.iterator().next(), equalTo("artifact~group~artifact~jar~1.0.lock"));
    }

    @Test
    public void singleArtifactWithClassifier() {
        DefaultArtifact artifact = new DefaultArtifact("group", "artifact", "classifier", "jar", "1.0");
        Collection<String> names = mapper.nameLocks(session, singletonList(artifact), null);

        assertThat(names, hasSize(1));
        assertThat(names.iterator().next(), equalTo("artifact~group~artifact~jar~classifier~1.0.lock"));
    }

    @Test
    public void singleMetadata() {
        DefaultMetadata metadata =
                new DefaultMetadata("group", "artifact", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<String> names = mapper.nameLocks(session, null, singletonList(metadata));

        assertThat(names, hasSize(1));
        assertThat(names.iterator().next(), equalTo("metadata~group~artifact.lock"));
    }

    @Test
    public void pathSeparatorsInArtifactCoordinatesAreNeutralized() {
        // wire-supplied coordinate fields must not be able to select a lock path outside the locks directory
        DefaultArtifact artifact = new DefaultArtifact("group", "artifact", "jar", "1/../../../../home/user/x");
        Collection<String> names = mapper.nameLocks(session, singletonList(artifact), null);

        assertThat(names, hasSize(1));
        String name = names.iterator().next();
        assertThat(
                name,
                equalTo(
                        "artifact~group~artifact~jar~1-SLASH-..-SLASH-..-SLASH-..-SLASH-..-SLASH-home-SLASH-user-SLASH-x.lock"));
        assertThat(name, Matchers.not(Matchers.containsString("/")));
        assertThat(name, Matchers.not(Matchers.containsString("\\")));
    }

    @Test
    public void pathSeparatorsInExtensionAreNeutralized() {
        // extension is one of the coordinate fields GAECV adds on top of GAV, must be neutralized the same way
        DefaultArtifact artifact = new DefaultArtifact("group", "artifact", "", "j/a\\r", "1.0");
        Collection<String> names = mapper.nameLocks(session, singletonList(artifact), null);

        assertThat(names, hasSize(1));
        String name = names.iterator().next();
        assertThat(name, equalTo("artifact~group~artifact~j-SLASH-a-BACKSLASH-r~1.0.lock"));
        assertThat(name, Matchers.not(Matchers.containsString("/")));
        assertThat(name, Matchers.not(Matchers.containsString("\\")));
    }

    @Test
    public void pathSeparatorsInClassifierAreNeutralized() {
        // classifier is the other coordinate field GAECV adds on top of GAV, must be neutralized the same way
        DefaultArtifact artifact = new DefaultArtifact("group", "artifact", "../../etc/passwd", "jar", "1.0");
        Collection<String> names = mapper.nameLocks(session, singletonList(artifact), null);

        assertThat(names, hasSize(1));
        String name = names.iterator().next();
        assertThat(name, equalTo("artifact~group~artifact~jar~..-SLASH-..-SLASH-etc-SLASH-passwd~1.0.lock"));
        assertThat(name, Matchers.not(Matchers.containsString("/")));
        assertThat(name, Matchers.not(Matchers.containsString("\\")));
    }

    @Test
    public void pathSeparatorsInMetadataCoordinatesAreNeutralized() {
        DefaultMetadata metadata = new DefaultMetadata(
                "group", "artifact", "1\\..\\..\\x", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<String> names = mapper.nameLocks(session, null, singletonList(metadata));

        assertThat(names, hasSize(1));
        String name = names.iterator().next();
        assertThat(name, equalTo("metadata~group~artifact~1-BACKSLASH-..-BACKSLASH-..-BACKSLASH-x.lock"));
        assertThat(name, Matchers.not(Matchers.containsString("/")));
        assertThat(name, Matchers.not(Matchers.containsString("\\")));
    }

    @Test
    public void oneAndOne() {
        DefaultArtifact artifact = new DefaultArtifact("agroup:artifact:1.0");
        DefaultMetadata metadata =
                new DefaultMetadata("bgroup", "artifact", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        Collection<String> names = mapper.nameLocks(session, singletonList(artifact), singletonList(metadata));

        assertThat(names, hasSize(2));
        Iterator<String> namesIterator = names.iterator();

        // they are sorted as well
        assertThat(namesIterator.next(), equalTo("artifact~agroup~artifact~jar~1.0.lock"));
        assertThat(namesIterator.next(), equalTo("metadata~bgroup~artifact.lock"));
    }
}
