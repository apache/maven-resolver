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

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class SubArtifactTest {

    private Artifact newMainArtifact(String coords) {
        return new DefaultArtifact(coords);
    }

    @Test
    void testMainArtifactFileNotRetained() {
        Artifact a = newMainArtifact("gid:aid:ver").setFile(new File(""));
        assertNotNull(a.getFile());
        a = new SubArtifact(a, "", "pom");
        assertNull(a.getFile());
    }

    @Test
    void testMainArtifactPropertiesNotRetained() {
        Artifact a = newMainArtifact("gid:aid:ver").setProperties(Collections.singletonMap("key", "value"));
        assertEquals(1, a.getProperties().size());
        a = new SubArtifact(a, "", "pom");
        assertEquals(0, a.getProperties().size());
        assertSame(null, a.getProperty("key", null));
    }

    @Test
    void testMainArtifactMissing() {
        assertThrows(NullPointerException.class, () -> new SubArtifact(null, "", "pom"));
    }

    @Test
    void testEmptyClassifier() {
        Artifact main = newMainArtifact("gid:aid:ext:cls:ver");
        Artifact sub = new SubArtifact(main, "", "pom");
        assertEquals("", sub.getClassifier());
        sub = new SubArtifact(main, null, "pom");
        assertEquals("", sub.getClassifier());
    }

    @Test
    void testEmptyExtension() {
        Artifact main = newMainArtifact("gid:aid:ext:cls:ver");
        Artifact sub = new SubArtifact(main, "tests", "");
        assertEquals("", sub.getExtension());
        sub = new SubArtifact(main, "tests", null);
        assertEquals("", sub.getExtension());
    }

    @Test
    void testSameClassifier() {
        Artifact main = newMainArtifact("gid:aid:ext:cls:ver");
        Artifact sub = new SubArtifact(main, "*", "pom");
        assertEquals("cls", sub.getClassifier());
    }

    @Test
    void testSameExtension() {
        Artifact main = newMainArtifact("gid:aid:ext:cls:ver");
        Artifact sub = new SubArtifact(main, "tests", "*");
        assertEquals("ext", sub.getExtension());
    }

    @Test
    void testDerivedClassifier() {
        Artifact main = newMainArtifact("gid:aid:ext:cls:ver");
        Artifact sub = new SubArtifact(main, "*-tests", "pom");
        assertEquals("cls-tests", sub.getClassifier());
        sub = new SubArtifact(main, "tests-*", "pom");
        assertEquals("tests-cls", sub.getClassifier());

        main = newMainArtifact("gid:aid:ext:ver");
        sub = new SubArtifact(main, "*-tests", "pom");
        assertEquals("tests", sub.getClassifier());
        sub = new SubArtifact(main, "tests-*", "pom");
        assertEquals("tests", sub.getClassifier());
    }

    @Test
    void testDerivedExtension() {
        Artifact main = newMainArtifact("gid:aid:ext:cls:ver");
        Artifact sub = new SubArtifact(main, "", "*.asc");
        assertEquals("ext.asc", sub.getExtension());
        sub = new SubArtifact(main, "", "asc.*");
        assertEquals("asc.ext", sub.getExtension());
    }

    @Test
    void testImmutability() {
        Artifact a = new SubArtifact(newMainArtifact("gid:aid:ver"), "", "pom");
        assertNotSame(a, a.setFile(new File("file")));
        assertNotSame(a, a.setVersion("otherVersion"));
        assertNotSame(a, a.setProperties(Collections.singletonMap("key", "value")));
    }

    @Test
    void testPropertiesCopied() {
        Map<String, String> props = new HashMap<>();
        props.put("key", "value1");

        Artifact a = new SubArtifact(newMainArtifact("gid:aid:ver"), "", "pom", props, (File) null);
        assertEquals("value1", a.getProperty("key", null));
        props.clear();
        assertEquals("value1", a.getProperty("key", null));

        props.put("key", "value2");
        a = a.setProperties(props);
        assertEquals("value2", a.getProperty("key", null));
        props.clear();
        assertEquals("value2", a.getProperty("key", null));
    }
}
