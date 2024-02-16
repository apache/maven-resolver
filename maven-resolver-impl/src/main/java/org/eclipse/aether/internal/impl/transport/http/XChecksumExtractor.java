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
package org.eclipse.aether.internal.impl.transport.http;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.aether.internal.impl.checksum.Md5ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.impl.checksum.Sha1ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.transport.http.ChecksumExtractorStrategy;

/**
 * Generic checksum extractor that goes for "X-" headers.
 */
@Singleton
@Named(XChecksumExtractor.NAME)
public final class XChecksumExtractor extends ChecksumExtractorStrategy {
    public static final String NAME = "xChecksum";

    @Override
    public Map<String, String> extractChecksums(Function<String, String> headerGetter) {
        String value;
        HashMap<String, String> result = new HashMap<>();
        // Central style: x-checksum-sha1: c74edb60ca2a0b57ef88d9a7da28f591e3d4ce7b
        value = headerGetter.apply("x-checksum-sha1");
        if (value != null) {
            result.put(Sha1ChecksumAlgorithmFactory.NAME, value);
        }
        // Central style: x-checksum-md5: 9ad0d8e3482767c122e85f83567b8ce6
        value = headerGetter.apply("x-checksum-md5");
        if (value != null) {
            result.put(Md5ChecksumAlgorithmFactory.NAME, value);
        }
        if (!result.isEmpty()) {
            return result;
        }
        // Google style: x-goog-meta-checksum-sha1: c74edb60ca2a0b57ef88d9a7da28f591e3d4ce7b
        value = headerGetter.apply("x-goog-meta-checksum-sha1");
        if (value != null) {
            result.put(Sha1ChecksumAlgorithmFactory.NAME, value);
        }
        // Central style: x-goog-meta-checksum-sha1: 9ad0d8e3482767c122e85f83567b8ce6
        value = headerGetter.apply("x-goog-meta-checksum-md5");
        if (value != null) {
            result.put(Md5ChecksumAlgorithmFactory.NAME, value);
        }
        return result.isEmpty() ? null : result;
    }
}
