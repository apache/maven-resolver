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

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

    /**
     * Header name prefixes, to be suffixed by lower case algorithm name. Tried in order, first prefix that yields
     * any checksum wins.
     */
    private static final List<String> HEADER_PREFIXES = Arrays.asList(
            // Central style: x-checksum-sha1: c74edb60ca2a0b57ef88d9a7da28f591e3d4ce7b
            "x-checksum-",
            // Google style: x-goog-meta-checksum-sha1: c74edb60ca2a0b57ef88d9a7da28f591e3d4ce7b
            "x-goog-meta-checksum-",
            // AWS S3 style: x-amz-meta-checksum-sha1: c74edb60ca2a0b57ef88d9a7da28f591e3d4ce7b
            "x-amz-meta-checksum-");

    @Override
    public Map<String, String> extractChecksums(Function<String, String> headerGetter) {
        for (String headerPrefix : HEADER_PREFIXES) {
            Map<String, String> result = extractChecksums(headerGetter, headerPrefix);
            if (!result.isEmpty()) {
                return result;
            }
        }
        return null;
    }

    private static Map<String, String> extractChecksums(Function<String, String> headerGetter, String headerPrefix) {
        HashMap<String, String> result = new HashMap<>();
        String value = headerGetter.apply(headerPrefix + "sha1");
        if (value != null) {
            result.put(Sha1ChecksumAlgorithmFactory.NAME, value);
        }
        value = headerGetter.apply(headerPrefix + "md5");
        if (value != null) {
            result.put(Md5ChecksumAlgorithmFactory.NAME, value);
        }
        return result;
    }
}
