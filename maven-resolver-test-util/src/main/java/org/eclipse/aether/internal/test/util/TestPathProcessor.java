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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.eclipse.aether.spi.io.PathProcessor;

/**
 * A simple file processor implementation to help satisfy component requirements during tests.
 */
public class TestPathProcessor implements PathProcessor {

    private final TestFileProcessor testFileProcessor = new TestFileProcessor();

    @Override
    public void setLastModified(Path path, long value) throws IOException {
        Files.setLastModifiedTime(path, FileTime.fromMillis(value));
    }

    public void mkdirs(Path directory) {
        if (directory == null) {
            return;
        }
        testFileProcessor.mkdirs(directory.toFile());
    }

    public void write(Path file, String data) throws IOException {
        testFileProcessor.write(file.toFile(), data);
    }

    public void write(Path target, InputStream source) throws IOException {
        testFileProcessor.write(target.toFile(), source);
    }

    public long copy(Path source, Path target, ProgressListener listener) throws IOException {
        return testFileProcessor.copy(source.toFile(), target.toFile(), null);
    }

    public void move(Path source, Path target) throws IOException {
        testFileProcessor.move(source.toFile(), target.toFile());
    }
}
