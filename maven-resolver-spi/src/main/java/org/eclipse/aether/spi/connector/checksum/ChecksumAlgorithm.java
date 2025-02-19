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

import java.nio.ByteBuffer;

/**
 * Implementation performing checksum calculation for specific algorithm. Instances of this interface are stateful,
 * non-thread safe, and should not be reused.
 *
 * @since 1.8.0
 */
public interface ChecksumAlgorithm {
    /**
     * Updates the checksum algorithm inner state with input.
     */
    void update(ByteBuffer input);

    /**
     * Returns the algorithm end result as string, never {@code null}. After invoking this method, this instance should
     * be discarded and not reused. For new checksum calculation you have to get new instance.
     *
     * Values returned by this method are handled as "opaque strings", and are used for simple equality checks (matches
     * or not matches the checksum), and are also persisted in this form (locally to file system but also uploaded as
     * checksum files). Resolver itself never tries to "decode" or "interpret" this string in any other way.
     */
    String checksum();
}
