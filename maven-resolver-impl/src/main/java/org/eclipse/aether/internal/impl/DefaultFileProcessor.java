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

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.util.FileUtils;

/**
 * A utility class helping with file-based operations.
 */
@Singleton
@Named
public class DefaultFileProcessor implements FileProcessor {

    /**
     * Thread-safe variant of {@link File#mkdirs()}. Creates the directory named by the given abstract pathname,
     * including any necessary but nonexistent parent directories. Note that if this operation fails it may have
     * succeeded in creating some of the necessary parent directories.
     *
     * @param directory The directory to create, may be {@code null}.
     * @return {@code true} if and only if the directory was created, along with all necessary parent directories;
     * {@code false} otherwise
     */
    @Override
    public boolean mkdirs(File directory) {
        if (directory == null) {
            return false;
        }

        if (directory.exists()) {
            return false;
        }
        if (directory.mkdir()) {
            return true;
        }

        File canonDir;
        try {
            canonDir = directory.getCanonicalFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        File parentDir = canonDir.getParentFile();
        return (parentDir != null && (mkdirs(parentDir) || parentDir.exists()) && canonDir.mkdir());
    }

    @Override
    public void write(File target, String data) throws IOException {
        FileUtils.writeFile(target.toPath(), p -> Files.write(p, data.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void write(File target, InputStream source) throws IOException {
        FileUtils.writeFile(target.toPath(), p -> Files.copy(source, p, StandardCopyOption.REPLACE_EXISTING));
    }

    @Override
    public void copy(File source, File target) throws IOException {
        copy(source, target, null);
    }

    @Override
    public long copy(File source, File target, ProgressListener listener) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(source.toPath()));
                FileUtils.CollocatedTempFile tempTarget = FileUtils.newTempFile(target.toPath());
                OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempTarget.getPath()))) {
            long result = copy(out, in, listener);
            tempTarget.move();
            return result;
        }
    }

    private long copy(OutputStream os, InputStream is, ProgressListener listener) throws IOException {
        long total = 0L;
        byte[] buffer = new byte[1024 * 32];
        while (true) {
            int bytes = is.read(buffer);
            if (bytes < 0) {
                break;
            }

            os.write(buffer, 0, bytes);

            total += bytes;

            if (listener != null && bytes > 0) {
                try {
                    listener.progressed(ByteBuffer.wrap(buffer, 0, bytes));
                } catch (Exception e) {
                    // too bad
                }
            }
        }

        return total;
    }

    @Override
    public void move(File source, File target) throws IOException {
        if (!source.renameTo(target)) {
            copy(source, target);

            target.setLastModified(source.lastModified());

            source.delete();
        }
    }

    @Override
    public String readChecksum(final File checksumFile) throws IOException {
        // for now do exactly same as happened before, but FileProcessor is a component and can be replaced
        String checksum = "";
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(Files.newInputStream(checksumFile.toPath()), StandardCharsets.UTF_8), 512)) {
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (!line.isEmpty()) {
                    checksum = line;
                    break;
                }
            }
        }

        if (checksum.matches(".+= [0-9A-Fa-f]+")) {
            int lastSpacePos = checksum.lastIndexOf(' ');
            checksum = checksum.substring(lastSpacePos + 1);
        } else {
            int spacePos = checksum.indexOf(' ');

            if (spacePos != -1) {
                checksum = checksum.substring(0, spacePos);
            }
        }

        return checksum;
    }

    @Override
    public void writeChecksum(final File checksumFile, final String checksum) throws IOException {
        // for now do exactly same as happened before, but FileProcessor is a component and can be replaced
        write(checksumFile, checksum);
    }
}
