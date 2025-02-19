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
package org.eclipse.aether.spi.connector.transport.http;

import java.util.Map;
import java.util.function.Function;

/**
 * Checksum extractor strategy, that aims for given type of included checksum.
 *
 * @since 2.0.0
 */
public abstract class ChecksumExtractorStrategy {
    /**
     * Extract checksums using given getter, if possible, or {@code null}.
     * <p>
     * The supplied {@code headerGetter} function should provide access to given transport response HTTP Headers in
     * some common way, like for example returning first header value, or {@code null} if header not present.
     *
     * @param headerGetter A function that provides access to response HTTP Headers, never {@code null}.
     * @return Map of extracted checksums, or {@code null} if none extracted.
     */
    public Map<String, String> extractChecksums(Function<String, String> headerGetter) {
        return null;
    }
}
