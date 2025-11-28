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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultTrackingFileManagerTest {
    public enum FS {
        DEFAULT,
        JIMFS_UNIX,
        JIMFS_WINDOWS;

        @Override
        public String toString() {
            return super.name().toLowerCase(Locale.ENGLISH);
        }
    }

    private FileSystem fileSystem;

    private Path createTmpFile(FS fs) throws IOException {
        return createTmpFile(fs, 1);
    }

    private Path createTmpFile(FS fs, int iterations) throws IOException {
        byte[] payload = "#COMMENT\nkey1=value1\nkey2 : value2\n".getBytes(StandardCharsets.UTF_8);
        if (iterations > 1) {
            int payloadLength = payload.length;
            byte[] newPayload = new byte[payloadLength * iterations];
            for (int i = 0; i < iterations; i++) {
                System.arraycopy(payload, 0, newPayload, i * payloadLength, payloadLength);
            }
            payload = newPayload;
        }
        Path tempFile;
        if (fs == FS.DEFAULT) {
            tempFile = Files.createTempFile(getClass().getSimpleName(), ".tmp");
        } else if (fs == FS.JIMFS_UNIX || fs == FS.JIMFS_WINDOWS) {
            fileSystem = Jimfs.newFileSystem(fs == FS.JIMFS_WINDOWS ? Configuration.windows() : Configuration.unix());
            tempFile = fileSystem.getPath("tmp/file.properties");
        } else {
            throw new IllegalStateException("unsupported FS: " + fs);
        }
        Files.createDirectories(tempFile.getParent());
        Files.write(tempFile, payload);
        return tempFile;
    }

    @AfterEach
    void cleanup() throws IOException {
        if (fileSystem != null) {
            fileSystem.close();
        }
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testRead(FS fs) throws Exception {
        TrackingFileManager tfm = new DefaultTrackingFileManager();

        Path propFile = createTmpFile(fs);
        Properties props = tfm.read(propFile);

        assertNotNull(props);
        assertEquals(2, props.size(), String.valueOf(props));
        assertEquals("value1", props.get("key1"));
        assertEquals("value2", props.get("key2"));

        assertDoesNotThrow(() -> Files.delete(propFile), "Leaked file" + propFile);

        props = tfm.read(propFile);
        assertNull(props, String.valueOf(props));
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testReadNoFileLeak(FS fs) throws Exception {
        TrackingFileManager tfm = new DefaultTrackingFileManager();

        for (int i = 0; i < 1000; i++) {
            Path propFile = createTmpFile(fs);
            assertNotNull(tfm.read(propFile));
            assertDoesNotThrow(() -> Files.delete(propFile), "Leaked file" + propFile);
        }
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testUpdate(FS fs) throws Exception {
        TrackingFileManager tfm = new DefaultTrackingFileManager();

        // NOTE: The excessive repetitions are to check the update properly truncates the file
        Path propFile = createTmpFile(fs, 1000);

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

    @ParameterizedTest
    @EnumSource(FS.class)
    void testUpdateNoFileLeak(FS fs) throws Exception {
        TrackingFileManager tfm = new DefaultTrackingFileManager();

        Map<String, String> updates = new HashMap<>();
        updates.put("k", "v");

        for (int i = 0; i < 1000; i++) {
            Path propFile = createTmpFile(fs);
            assertNotNull(tfm.update(propFile, updates));
            assertDoesNotThrow(() -> Files.delete(propFile), "Leaked file" + propFile);
        }
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    public void testDeleteFileIsGone(FS fs) throws Exception {
        TrackingFileManager tfm = new DefaultTrackingFileManager();

        for (int i = 0; i < 1000; i++) {
            Path propFile = createTmpFile(fs);
            assertTrue(tfm.delete(propFile));
            assertFalse(Files.exists(propFile), "File is not gone");
        }
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testLockingOnCanonicalPath(FS fs) throws Exception {
        final TrackingFileManager tfm = new DefaultTrackingFileManager();

        Path propFile = createTmpFile(fs);

        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        Thread[] threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            String path = propFile.getParent().toString();
            for (int j = 0; j < i; j++) {
                path += "/.";
            }
            path += "/" + propFile.getFileName();
            final Path file = fileSystem != null ? fileSystem.getPath(path) : Paths.get(path);

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
