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
package org.eclipse.aether.spi.connector.checksum;

import static java.util.Objects.requireNonNull;

/**
 * Support class for {@link ChecksumAlgorithmFactory} implementations.
 *
 * @since 1.8.0
 */
public abstract class ChecksumAlgorithmFactorySupport implements ChecksumAlgorithmFactory {
    private final String name;

    private final String fileExtension;

    public ChecksumAlgorithmFactorySupport(String name, String fileExtension) {
        this.name = requireNonNull(name);
        this.fileExtension = requireNonNull(fileExtension);
    }

    /**
     * Returns the algorithm name, usually used as key, never {@code null} value.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns the file extension to be used for given checksum algorithm (without leading dot), never {@code null}.
     */
    @Override
    public String getFileExtension() {
        return fileExtension;
    }
}
