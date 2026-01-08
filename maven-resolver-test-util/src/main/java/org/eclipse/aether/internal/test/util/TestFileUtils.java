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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.UUID;

/**
 * Provides utility methods to read and write (temporary) files.
 */
public class TestFileUtils {

    private static final File TMP = new File(
            System.getProperty("java.io.tmpdir"),
            "aether-" + UUID.randomUUID().toString().substring(0, 8));

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                deleteFile(TMP);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
    }

    private TestFileUtils() {
        // hide constructor
    }

    @Deprecated
    public static void deleteTempFiles() throws IOException {
        deleteFile(TMP);
    }

    public static void deleteFile(File file) throws IOException {
        if (file == null) {
            return;
        }

        Collection<File> undeletables = new ArrayList<>();

        delete(file, undeletables);

        if (!undeletables.isEmpty()) {
            throw new IOException("Failed to delete " + undeletables);
        }
    }

    private static void delete(File file, Collection<File> undeletables) {
        String[] children = file.list();
        if (children != null) {
            for (String child : children) {
                delete(new File(file, child), undeletables);
            }
        }

        if (!del(file)) {
            undeletables.add(file.getAbsoluteFile());
        }
    }

    private static boolean del(File file) {
        for (int i = 0; i < 10; i++) {
            if (file.delete() || !file.exists()) {
                return true;
            }
        }
        return false;
    }

    @Deprecated
    public static boolean mkdirs(File directory) {
        if (directory == null) {
            return false;
        }

        if (directory.exists()) {
            return false;
        }
        if (directory.mkdir()) {
            return true;
        }

        File canonDir = null;
        try {
            canonDir = directory.getCanonicalFile();
        } catch (IOException e) {
            return false;
        }

        File parentDir = canonDir.getParentFile();
        return (parentDir != null && (mkdirs(parentDir) || parentDir.exists()) && canonDir.mkdir());
    }

    /**
     * @throws IOException if an I/O error occurs
     * @deprecated use @TempDir (JUnit 5) Or TemporaryFolder (JUnit 4) instead
     */
    @Deprecated
    public static File createTempFile(String contents) throws IOException {
        return createTempFile(contents.getBytes(StandardCharsets.UTF_8), 1);
    }

    @Deprecated
    /**
     * @throws IOException if an I/O error occurs
     * @deprecated use @TempDir (JUnit 5) Or TemporaryFolder (JUnit 4) instead
     */
    public static File createTempFile(byte[] pattern, int repeat) throws IOException {
        mkdirs(TMP);
        File tmpFile = File.createTempFile("tmpfile-", ".data", TMP);
        writeBytes(tmpFile, pattern, repeat);
        return tmpFile;
    }

    /**
     * Creates a temporary directory.
     *
     * @return the temporary directory
     * @throws IOException if an I/O error occurs
     * @deprecated use @TempDir (JUnit 5) Or TemporaryFolder (JUnit 4) instead
     */
    @Deprecated
    public static File createTempDir() throws IOException {
        return createTempDir("");
    }

    /**
     * Creates a temporary directory.
     *
     * @return the temporary directory
     * @throws IOException if an I/O error occurs
     * @deprecated use {@code @TempDir} (JUnit 5) or {@code TemporaryFolder} (JUnit
     *             4) instead
     */
    @Deprecated
    public static File createTempDir(String suffix) throws IOException {
        mkdirs(TMP);
        File tmpFile = File.createTempFile("tmpdir-", suffix, TMP);
        deleteFile(tmpFile);
        mkdirs(tmpFile);
        return tmpFile;
    }

    public static long copyFile(File source, File target) throws IOException {
        long total = 0;

        mkdirs(target.getParentFile());

        try (FileInputStream fis = new FileInputStream(source);
                OutputStream fos = new BufferedOutputStream(new FileOutputStream(target))) {

            for (byte[] buffer = new byte[1024 * 32];;) {
                int bytes = fis.read(buffer);
                if (bytes < 0) {
                    break;
                }

                fos.write(buffer, 0, bytes);

                total += bytes;
            }
        }

        return total;
    }

    /**
     * Reads the contents of a file into a byte array.
     *
     * @param file the file to read
     * @return the contents of the file as a byte array
     * @throws IOException if an I/O error occurs
     * @deprecated use {@code Files.readAllBytes(Path)} instead
     */
    @Deprecated
    public static byte[] readBytes(File file) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            byte[] actual = new byte[(int) in.length()];
            in.readFully(actual);
            return actual;
        }
    }

    @Deprecated
    public static void writeBytes(File file, byte[] pattern, int repeat) throws IOException {
        file.deleteOnExit();
        file.getParentFile().mkdirs();
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < repeat; i++) {
                out.write(pattern);
            }
        }
    }

    public static String readString(File file) throws IOException {
        byte[] content = Files.readAllBytes(file.toPath());
        return new String(content, StandardCharsets.UTF_8);
    }

    @Deprecated
    public static void writeString(File file, String content) throws IOException {
        writeBytes(file, content.getBytes(StandardCharsets.UTF_8), 1);
    }

    @Deprecated
    public static void writeString(File file, String content, long timestamp) throws IOException {
        writeBytes(file, content.getBytes(StandardCharsets.UTF_8), 1);
        file.setLastModified(timestamp);
    }

    public static void readProps(File file, Properties props) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
        }
    }

    public static void writeProps(File file, Properties props) throws IOException {
        file.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            props.store(fos, "aether-test");
        }
    }
}
