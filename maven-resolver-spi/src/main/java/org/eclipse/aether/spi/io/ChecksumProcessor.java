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
package org.eclipse.aether.spi.io;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A utility component to perform checksum related operations.
 *
 * @since 2.0.0
 */
public interface ChecksumProcessor {
    /**
     * Reads checksum from specified file.
     *
     * @throws IOException in case of any IO error.
     * @since 1.8.0
     */
    String readChecksum(Path checksumFile) throws IOException;

    /**
     * Writes checksum to specified file.
     *
     * @throws IOException in case of any IO error.
     * @since 1.8.0
     */
    void writeChecksum(Path checksumFile, String checksum) throws IOException;
}
