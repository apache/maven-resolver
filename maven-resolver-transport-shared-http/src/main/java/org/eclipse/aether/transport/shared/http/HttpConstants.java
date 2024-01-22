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

import java.util.regex.Pattern;

/**
 * Some shared HTTP constants.
 *
 * @since 2.0.0
 */
public final class HttpConstants {
    private HttpConstants() {}

    public static final int MULTIPLE_CHOICES = 300;

    public static final int NOT_FOUND = 404;

    public static final int PRECONDITION_FAILED = 412;

    public static final String ACCEPT_ENCODING = "Accept-Encoding";

    public static final String CACHE_CONTROL = "Cache-Control";

    public static final String CONTENT_LENGTH = "Content-Length";

    public static final String CONTENT_RANGE = "Content-Range";

    public static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";

    public static final String RANGE = "Range";

    public static final String USER_AGENT = "User-Agent";

    public static final String LAST_MODIFIED = "Last-Modified";

    public static final Pattern CONTENT_RANGE_PATTERN =
            Pattern.compile("\\s*bytes\\s+([0-9]+)\\s*-\\s*([0-9]+)\\s*/.*");
}
