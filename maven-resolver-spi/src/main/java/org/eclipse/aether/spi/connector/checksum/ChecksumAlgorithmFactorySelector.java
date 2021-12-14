package org.eclipse.aether.spi.connector.checksum;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Set;

/**
 * Component performing selection of {@link ChecksumAlgorithmFactory} based on known factory names.
 *
 * @since 1.8.0
 */
public interface ChecksumAlgorithmFactorySelector
{
    /**
     * Returns factory for given algorithm name, or throws if algorithm not supported.
     *
     * @throws IllegalArgumentException if asked algorithm name is not supported.
     */
    ChecksumAlgorithmFactory select( String algorithmName );

    /**
     * Returns a set of supported algorithm names. This set represents ALL the algorithms supported by Resolver, and is
     * NOT in any relation to given repository layout used checksums, returned by method {@link
     * org.eclipse.aether.spi.connector.layout.RepositoryLayout#getChecksumAlgorithmNames()} (is super set of it).
     */
    Set<String> getChecksumAlgorithmNames();
}
