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
package org.eclipse.aether.internal.impl;

/**
 * Sanitizes strings that may carry remote-derived bytes (artifact coordinates, checksum values, transfer error
 * messages) before they are logged or persisted. Bytes received from a remote repository must not be able to
 * influence terminal rendering: raw control characters such as ESC (ANSI escape sequences) or CR (line rewrite)
 * embedded in, for example, a transitive POM's coordinates could otherwise erase or forge log lines - including
 * the sole integrity WARNING emitted under the default "warn" checksum policy.
 *
 * @since 2.0.22
 */
final class LogSanitizer {
    private LogSanitizer() {}

    /**
     * Replaces every ISO control character below U+0020 (except LF and TAB) as well as DEL (U+007F) with its
     * visible {@code \}{@code uXXXX} escape, preserving the evidence while neutralizing terminal escape
     * sequences. Returns the input instance unchanged (no allocation) when nothing needs escaping; returns
     * {@code null} for {@code null} input.
     */
    static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder result = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < 0x20 && c != '\n' && c != '\t') || c == 0x7f) {
                if (result == null) {
                    result = new StringBuilder(value.length() + 16);
                    result.append(value, 0, i);
                }
                result.append(String.format("\\u%04X", (int) c));
            } else if (result != null) {
                result.append(c);
            }
        }
        return result == null ? value : result.toString();
    }
}
