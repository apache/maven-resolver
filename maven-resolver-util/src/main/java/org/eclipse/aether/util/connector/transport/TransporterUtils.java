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
package org.eclipse.aether.util.connector.transport;

import java.net.URI;

/**
 * A utility class for transporters.
 *
 * @since 2.0.25
 */
public final class TransporterUtils {
    private TransporterUtils() {}

    /**
     * Checks whether two URIs have the same origin (scheme, host and effective port).
     *
     * @param origin the repository origin
     * @param target the URI to compare with the origin
     * @return {@code true} when both URIs have the same origin
     * @since 2.0.25
     */
    public static boolean isSameOrigin(URI origin, URI target) {
        if (origin.getScheme() == null
                || origin.getHost() == null
                || target.getScheme() == null
                || target.getHost() == null) {
            return false;
        }
        return origin.getScheme().equalsIgnoreCase(target.getScheme())
                && origin.getHost().equalsIgnoreCase(target.getHost())
                && effectivePort(origin) == effectivePort(target);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : -1;
    }
}
