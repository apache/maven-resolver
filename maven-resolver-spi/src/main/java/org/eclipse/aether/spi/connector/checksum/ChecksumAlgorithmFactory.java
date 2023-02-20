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

/**
 * A component representing a checksum factory: provides {@link ChecksumAlgorithm} instances, name and extension to be
 * used with this algorithm. While directly injecting components of this type is possible, it is not recommended. To
 * obtain factory instances use {@link ChecksumAlgorithmFactorySelector} instead.
 *
 * @since 1.8.0
 */
public interface ChecksumAlgorithmFactory {
    /**
     * Returns the algorithm name, usually used as key, never {@code null} value. The name is a standard name of
     * algorithm (if applicable) or any other designator that is algorithm commonly referred with. Example: "SHA-1".
     */
    String getName();

    /**
     * Returns the file extension to be used for given checksum file (without leading dot), never {@code null}. The
     * extension should be file and URL path friendly, and may differ from value returned by {@link #getName()}.
     * The checksum extension SHOULD NOT contain dot (".") character.
     * Example: "sha1".
     */
    String getFileExtension();

    /**
     * Each invocation of this method returns a new instance of algorithm, never {@code null} value.
     */
    ChecksumAlgorithm getAlgorithm();
}
