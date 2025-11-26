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
package org.eclipse.aether.internal.impl.fs;

import java.io.File;
import java.nio.file.Path;

/**
 * The DefaultTrackingTestFile class extends the File class and provides additional functionality
 * for working with files using a safe Path object.
 * This class ensures file operations are referenced and handled using the encapsulated Path instance.
 */
public class DefaultTrackingTestFile extends File {
    private final Path safePath;

    public DefaultTrackingTestFile(String pathname, Path safePath) {
        super(pathname);
        this.safePath = safePath;
    }

    @Override
    public Path toPath() {
        return safePath;
    }

    @Override
    public String getCanonicalPath() {
        return safePath.toString();
    }
}
