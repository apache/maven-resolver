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
package org.eclipse.aether.artifact;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 */
public class DefaultArtifactTest {

    @Test
    public void testDefaultArtifactString() {
        Artifact a;

        a = new DefaultArtifact("gid:aid:ver");
        assertEquals("gid", a.getGroupId());
        assertEquals("aid", a.getArtifactId());
        assertEquals("ver", a.getVersion());
        assertEquals("ver", a.getBaseVersion());
        assertEquals("jar", a.getExtension());
        assertEquals("", a.getClassifier());

        a = new DefaultArtifact("gid:aid:ext:ver");
        assertEquals("gid", a.getGroupId());
        assertEquals("aid", a.getArtifactId());
        assertEquals("ver", a.getVersion());
        assertEquals("ver", a.getBaseVersion());
        assertEquals("ext", a.getExtension());
        assertEquals("", a.getClassifier());

        a = new DefaultArtifact("org.gid:foo-bar:jar:1.1-20101116.150650-3");
        assertEquals("org.gid", a.getGroupId());
        assertEquals("foo-bar", a.getArtifactId());
        assertEquals("1.1-20101116.150650-3", a.getVersion());
        assertEquals("1.1-SNAPSHOT", a.getBaseVersion());
        assertEquals("jar", a.getExtension());
        assertEquals("", a.getClassifier());

        a = new DefaultArtifact("gid:aid:ext:cls:ver");
        assertEquals("gid", a.getGroupId());
        assertEquals("aid", a.getArtifactId());
        assertEquals("ver", a.getVersion());
        assertEquals("ver", a.getBaseVersion());
        assertEquals("ext", a.getExtension());
        assertEquals("cls", a.getClassifier());

        a = new DefaultArtifact("gid:aid::cls:ver");
        assertEquals("gid", a.getGroupId());
        assertEquals("aid", a.getArtifactId());
        assertEquals("ver", a.getVersion());
        assertEquals("ver", a.getBaseVersion());
        assertEquals("jar", a.getExtension());
        assertEquals("cls", a.getClassifier());

        a = new DefaultArtifact(new DefaultArtifact("gid:aid:ext:cls:ver").toString());
        assertEquals("gid", a.getGroupId());
        assertEquals("aid", a.getArtifactId());
        assertEquals("ver", a.getVersion());
        assertEquals("ver", a.getBaseVersion());
        assertEquals("ext", a.getExtension());
        assertEquals("cls", a.getClassifier());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDefaultArtifactContainsGroupAndArtifactOnly() {
        new DefaultArtifact("gid:aid");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDefaultArtifactContainsGroupOnly() {
        new DefaultArtifact("gid");
    }

    @Test
    public void testImmutability() {
        Artifact a = new DefaultArtifact("gid:aid:ext:cls:ver");
        assertNotSame(a, a.setFile(new File("file")));
        assertNotSame(a, a.setVersion("otherVersion"));
        assertNotSame(a, a.setProperties(Collections.singletonMap("key", "value")));
    }

    @Test
    public void testArtifactType() {
        DefaultArtifactType type = new DefaultArtifactType("typeId", "typeExt", "typeCls", "typeLang", true, true);

        Artifact a = new DefaultArtifact("gid", "aid", null, null, null, null, type);
        assertEquals("typeExt", a.getExtension());
        assertEquals("typeCls", a.getClassifier());
        assertEquals("typeLang", a.getProperties().get(ArtifactProperties.LANGUAGE));
        assertEquals("typeId", a.getProperties().get(ArtifactProperties.TYPE));
        assertEquals("true", a.getProperties().get(ArtifactProperties.INCLUDES_DEPENDENCIES));
        assertEquals("true", a.getProperties().get(ArtifactProperties.CONSTITUTES_BUILD_PATH));

        a = new DefaultArtifact("gid", "aid", "cls", "ext", "ver", null, type);
        assertEquals("ext", a.getExtension());
        assertEquals("cls", a.getClassifier());
        assertEquals("typeLang", a.getProperties().get(ArtifactProperties.LANGUAGE));
        assertEquals("typeId", a.getProperties().get(ArtifactProperties.TYPE));
        assertEquals("true", a.getProperties().get(ArtifactProperties.INCLUDES_DEPENDENCIES));
        assertEquals("true", a.getProperties().get(ArtifactProperties.CONSTITUTES_BUILD_PATH));

        Map<String, String> props = new HashMap<>();
        props.put("someNonStandardProperty", "someNonStandardProperty");
        a = new DefaultArtifact("gid", "aid", "cls", "ext", "ver", props, type);
        assertEquals("ext", a.getExtension());
        assertEquals("cls", a.getClassifier());
        assertEquals("typeLang", a.getProperties().get(ArtifactProperties.LANGUAGE));
        assertEquals("typeId", a.getProperties().get(ArtifactProperties.TYPE));
        assertEquals("true", a.getProperties().get(ArtifactProperties.INCLUDES_DEPENDENCIES));
        assertEquals("true", a.getProperties().get(ArtifactProperties.CONSTITUTES_BUILD_PATH));
        assertEquals("someNonStandardProperty", a.getProperties().get("someNonStandardProperty"));

        props = new HashMap<>();
        props.put("someNonStandardProperty", "someNonStandardProperty");
        props.put(ArtifactProperties.CONSTITUTES_BUILD_PATH, "rubbish");
        props.put(ArtifactProperties.INCLUDES_DEPENDENCIES, "rubbish");
        a = new DefaultArtifact("gid", "aid", "cls", "ext", "ver", props, type);
        assertEquals("ext", a.getExtension());
        assertEquals("cls", a.getClassifier());
        assertEquals("typeLang", a.getProperties().get(ArtifactProperties.LANGUAGE));
        assertEquals("typeId", a.getProperties().get(ArtifactProperties.TYPE));
        assertEquals("rubbish", a.getProperties().get(ArtifactProperties.INCLUDES_DEPENDENCIES));
        assertEquals("rubbish", a.getProperties().get(ArtifactProperties.CONSTITUTES_BUILD_PATH));
        assertEquals("someNonStandardProperty", a.getProperties().get("someNonStandardProperty"));
    }

    @Test
    public void testPropertiesCopied() {
        Map<String, String> props = new HashMap<>();
        props.put("key", "value1");

        Artifact a = new DefaultArtifact("gid:aid:1", props);
        assertEquals("value1", a.getProperty("key", null));
        props.clear();
        assertEquals("value1", a.getProperty("key", null));

        props.put("key", "value2");
        a = a.setProperties(props);
        assertEquals("value2", a.getProperty("key", null));
        props.clear();
        assertEquals("value2", a.getProperty("key", null));
    }

    @Test
    public void testIsSnapshot() {
        Artifact a = new DefaultArtifact("gid:aid:ext:cls:1.0");
        assertFalse(a.getVersion(), a.isSnapshot());

        a = new DefaultArtifact("gid:aid:ext:cls:1.0-SNAPSHOT");
        assertTrue(a.getVersion(), a.isSnapshot());

        a = new DefaultArtifact("gid:aid:ext:cls:1.0-20101116.150650-3");
        assertTrue(a.getVersion(), a.isSnapshot());

        a = new DefaultArtifact("gid:aid:ext:cls:1.0-20101116x150650-3");
        assertFalse(a.getVersion(), a.isSnapshot());
    }
}
