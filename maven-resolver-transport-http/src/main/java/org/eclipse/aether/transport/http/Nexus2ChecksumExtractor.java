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
package org.eclipse.aether.transport.http;

import java.util.Collections;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;

/**
 * A component extracting Nexus2 ETag "shielded" style-checksums from response headers.
 *
 * @since 1.8.0
 */
@Singleton
@Named(Nexus2ChecksumExtractor.NAME)
public class Nexus2ChecksumExtractor extends ChecksumExtractor {
    public static final String NAME = "nexus2";

    @Override
    public Map<String, String> extractChecksums(HttpResponse response) {
        // Nexus-style, ETag: "{SHA1{d40d68ba1f88d8e9b0040f175a6ff41928abd5e7}}"
        Header header = response.getFirstHeader(HttpHeaders.ETAG);
        String etag = header != null ? header.getValue() : null;
        if (etag != null) {
            int start = etag.indexOf("SHA1{"), end = etag.indexOf("}", start + 5);
            if (start >= 0 && end > start) {
                return Collections.singletonMap("SHA-1", etag.substring(start + 5, end));
            }
        }
        return null;
    }
}
