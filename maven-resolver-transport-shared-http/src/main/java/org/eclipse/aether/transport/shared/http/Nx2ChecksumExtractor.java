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
package org.eclipse.aether.transport.shared.http;

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

/**
 * Sonatype Nexus2 checksum extractor.
 */
@Singleton
@Named(Nx2ChecksumExtractor.NAME)
public final class Nx2ChecksumExtractor implements ChecksumExtractor.Strategy {
    public static final String NAME = "nx2";
    private static final String ETAG = "ETag";

    @Override
    public Map<String, String> extractChecksums(Function<String, String> headerGetter) {
        // Nexus-style, ETag: "{SHA1{d40d68ba1f88d8e9b0040f175a6ff41928abd5e7}}"
        String etag = headerGetter.apply(ETAG);
        if (etag != null) {
            int start = etag.indexOf("SHA1{"), end = etag.indexOf("}", start + 5);
            if (start >= 0 && end > start) {
                return Collections.singletonMap("SHA-1", etag.substring(start + 5, end));
            }
        }
        return null;
    }
}
