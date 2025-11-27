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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class DefaultTrackingFileManagerTest {

    @Test
    void testRead() throws Exception {
        TrackingFileManager tfm = new DefaultTrackingFileManager();

        File propFile = TestFileUtils.createTempFile("#COMMENT\nkey1=value1\nkey2 : value2");
        Properties props = tfm.read(propFile);

        assertNotNull(props);
        assertEquals(2, props.size(), String.valueOf(props));
        assertEquals("value1", props.get("key1"));
        assertEquals("value2", props.get("key2"));

        assertTrue(propFile.delete(), "Leaked file: " + propFile);

        props = tfm.read(propFile);
        assertNull(props, String.valueOf(props));
    }

    @Test
    void testReadNoFileLeak() throws Exception {
        TrackingFileManager tfm = new DefaultTrackingFileManager();

        for (int i = 0; i < 1000; i++) {
            File propFile = TestFileUtils.createTempFile("#COMMENT\nkey1=value1\nkey2 : value2");
            assertNotNull(tfm.read(propFile));
            assertTrue(propFile.delete(), "Leaked file: " + propFile);
        }
    }

    @Test
    void testUpdate() throws Exception {
        TrackingFileManager tfm = new DefaultTrackingFileManager();

        // NOTE: The excessive repetitions are to check the update properly truncates the file
        File propFile =
                TestFileUtils.createTempFile("key1=value1\nkey2 : value2\n".getBytes(StandardCharsets.UTF_8), 1000);

        Map<String, String> updates = new HashMap<>();
        updates.put("key1", "v");
        updates.put("key2", null);

        tfm.update(propFile, updates);

        Properties props = tfm.read(propFile);

        assertNotNull(props);
        assertEquals(1, props.size(), String.valueOf(props));
        assertEquals("v", props.get("key1"));
        assertNull(props.get("key2"), String.valueOf(props.get("key2")));
    }

    @Test
    void testUpdateNoFileLeak() throws Exception {
        TrackingFileManager tfm = new DefaultTrackingFileManager();

        Map<String, String> updates = new HashMap<>();
        updates.put("k", "v");

        for (int i = 0; i < 1000; i++) {
            File propFile = TestFileUtils.createTempFile("#COMMENT\nkey1=value1\nkey2 : value2");
            assertNotNull(tfm.update(propFile, updates));
            assertTrue(propFile.delete(), "Leaked file: " + propFile);
        }
    }

    @Test
    public void testDeleteFileIsGone() throws Exception {
        TrackingFileManager tfm = new DefaultTrackingFileManager();

        for (int i = 0; i < 1000; i++) {
            File propFile = TestFileUtils.createTempFile("#COMMENT\nkey1=value1\nkey2 : value2");
            assertTrue(tfm.delete(propFile.toPath()));
            assertFalse(Files.isRegularFile(propFile.toPath()), "File is not gone");
        }
    }

    @Test
    void testLockingOnCanonicalPath() throws Exception {
        final TrackingFileManager tfm = new DefaultTrackingFileManager();

        final File propFile = TestFileUtils.createTempFile("#COMMENT\nkey1=value1\nkey2 : value2");

        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        Thread[] threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            String path = propFile.getParent();
            for (int j = 0; j < i; j++) {
                path += "/.";
            }
            path += "/" + propFile.getName();
            final File file = new File(path);

            threads[i] = new Thread(() -> {
                try {
                    for (int i1 = 0; i1 < 1000; i1++) {
                        assertNotNull(tfm.read(file));
                        HashMap<String, String> update = new HashMap<>();
                        update.put("wasHere", Thread.currentThread().getName() + "-" + i1);
                        tfm.update(file, update);
                    }
                } catch (Throwable e) {
                    errors.add(e);
                }
            });
        }

        for (Thread thread1 : threads) {
            thread1.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(Collections.emptyList(), errors);
    }
}
