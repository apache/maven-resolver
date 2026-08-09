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

import java.util.Map;
import java.util.TreeMap;

import org.eclipse.aether.internal.impl.checksum.Md5ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.impl.checksum.Sha1ChecksumAlgorithmFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UT for {@link XChecksumExtractor}.
 */
class XChecksumExtractorTest {
    private static final String SHA1 = "c74edb60ca2a0b57ef88d9a7da28f591e3d4ce7b";

    private static final String MD5 = "9ad0d8e3482767c122e85f83567b8ce6";

    private final XChecksumExtractor extractor = new XChecksumExtractor();

    // HTTP header names are case insensitive, and transports return null for absent headers
    private final TreeMap<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private Map<String, String> extract() {
        return extractor.extractChecksums(headers::get);
    }

    @Test
    void noHeaders() {
        assertNull(extract());
    }

    @Test
    void unrelatedHeaders() {
        headers.put("ETag", "\"deadbeef\"");
        headers.put("Content-Length", "42");
        assertNull(extract());
    }

    @Test
    void central() {
        headers.put("x-checksum-sha1", SHA1);
        headers.put("x-checksum-md5", MD5);
        Map<String, String> result = extract();
        assertEquals(2, result.size());
        assertEquals(SHA1, result.get(Sha1ChecksumAlgorithmFactory.NAME));
        assertEquals(MD5, result.get(Md5ChecksumAlgorithmFactory.NAME));
    }

    @Test
    void centralSha1Only() {
        headers.put("x-checksum-sha1", SHA1);
        Map<String, String> result = extract();
        assertEquals(1, result.size());
        assertEquals(SHA1, result.get(Sha1ChecksumAlgorithmFactory.NAME));
    }

    @Test
    void centralMd5Only() {
        headers.put("x-checksum-md5", MD5);
        Map<String, String> result = extract();
        assertEquals(1, result.size());
        assertEquals(MD5, result.get(Md5ChecksumAlgorithmFactory.NAME));
    }

    @Test
    void google() {
        headers.put("x-goog-meta-checksum-sha1", SHA1);
        headers.put("x-goog-meta-checksum-md5", MD5);
        Map<String, String> result = extract();
        assertEquals(2, result.size());
        assertEquals(SHA1, result.get(Sha1ChecksumAlgorithmFactory.NAME));
        assertEquals(MD5, result.get(Md5ChecksumAlgorithmFactory.NAME));
    }

    @Test
    void googleSha1Only() {
        headers.put("x-goog-meta-checksum-sha1", SHA1);
        Map<String, String> result = extract();
        assertEquals(1, result.size());
        assertEquals(SHA1, result.get(Sha1ChecksumAlgorithmFactory.NAME));
    }

    @Test
    void googleMd5Only() {
        headers.put("x-goog-meta-checksum-md5", MD5);
        Map<String, String> result = extract();
        assertEquals(1, result.size());
        assertEquals(MD5, result.get(Md5ChecksumAlgorithmFactory.NAME));
    }

    @Test
    void amazon() {
        headers.put("x-amz-meta-checksum-sha1", SHA1);
        headers.put("x-amz-meta-checksum-md5", MD5);
        Map<String, String> result = extract();
        assertEquals(2, result.size());
        assertEquals(SHA1, result.get(Sha1ChecksumAlgorithmFactory.NAME));
        assertEquals(MD5, result.get(Md5ChecksumAlgorithmFactory.NAME));
    }

    @Test
    void amazonSha1Only() {
        headers.put("x-amz-meta-checksum-sha1", SHA1);
        Map<String, String> result = extract();
        assertEquals(1, result.size());
        assertEquals(SHA1, result.get(Sha1ChecksumAlgorithmFactory.NAME));
    }

    @Test
    void amazonMd5Only() {
        headers.put("x-amz-meta-checksum-md5", MD5);
        Map<String, String> result = extract();
        assertEquals(1, result.size());
        assertEquals(MD5, result.get(Md5ChecksumAlgorithmFactory.NAME));
    }

    @Test
    void amazonMixedCaseHeaderNames() {
        headers.put("X-Amz-Meta-Checksum-Sha1", SHA1);
        headers.put("X-AMZ-META-CHECKSUM-MD5", MD5);
        Map<String, String> result = extract();
        assertEquals(2, result.size());
        assertEquals(SHA1, result.get(Sha1ChecksumAlgorithmFactory.NAME));
        assertEquals(MD5, result.get(Md5ChecksumAlgorithmFactory.NAME));
    }

    @Test
    void amazonAmongOtherAmzMetadata() {
        headers.put("x-amz-request-id", "K2ZQ0RYZ6H2VJTVX");
        headers.put("x-amz-meta-something", "irrelevant");
        headers.put("x-amz-meta-checksum-sha1", SHA1);
        Map<String, String> result = extract();
        assertEquals(1, result.size());
        assertEquals(SHA1, result.get(Sha1ChecksumAlgorithmFactory.NAME));
    }

    @Test
    void centralWinsOverGoogleAndAmazon() {
        headers.put("x-checksum-sha1", SHA1);
        headers.put("x-goog-meta-checksum-sha1", "0000000000000000000000000000000000000000");
        headers.put("x-amz-meta-checksum-sha1", "1111111111111111111111111111111111111111");
        Map<String, String> result = extract();
        assertEquals(1, result.size());
        assertEquals(SHA1, result.get(Sha1ChecksumAlgorithmFactory.NAME));
    }

    @Test
    void googleWinsOverAmazon() {
        // whole prefixes are tried in order, so the Amazon MD5 must not leak into the Google result
        headers.put("x-goog-meta-checksum-sha1", SHA1);
        headers.put("x-amz-meta-checksum-md5", MD5);
        Map<String, String> result = extract();
        assertEquals(1, result.size());
        assertEquals(SHA1, result.get(Sha1ChecksumAlgorithmFactory.NAME));
    }
}
