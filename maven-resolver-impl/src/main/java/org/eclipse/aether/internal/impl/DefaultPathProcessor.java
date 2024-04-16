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

import javax.inject.Named;
import javax.inject.Singleton;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;

import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class helping with file-based operations.
 */
@Singleton
@Named
public class DefaultPathProcessor implements PathProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void setLastModified(Path path, long value) throws IOException {
        try {
            Files.setLastModifiedTime(path, FileTime.fromMillis(value));
        } catch (FileSystemException e) {
            // MRESOLVER-536: Java uses generic FileSystemException for some weird cases,
            // but some subclasses like AccessDeniedEx should be re-thrown
            if (e instanceof AccessDeniedException) {
                throw e;
            }
            logger.debug("Failed to set last-modified: {}", path, e);
        }
    }

    @Override
    public void write(Path target, String data) throws IOException {
        FileUtils.writeFile(target, p -> Files.write(p, data.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void write(Path target, InputStream source) throws IOException {
        FileUtils.writeFile(target, p -> Files.copy(source, p, StandardCopyOption.REPLACE_EXISTING));
    }

    @Override
    public long copy(Path source, Path target, ProgressListener listener) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(source));
                FileUtils.CollocatedTempFile tempTarget = FileUtils.newTempFile(target);
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
    public void move(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }
}
