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
package org.eclipse.aether.repository;

import java.net.URI;
import java.nio.file.Paths;

/**
 * URL handling for file URLs. Based on org.apache.maven.wagon.PathUtils.
 *
 * @since 2.0.0
 */
public final class RepositoryUriUtils {

    private RepositoryUriUtils() {}

    /**
     * Converts the given repository URL or file path to a {@link URI}, resolving plain file paths and
     * {@code file:} URLs against the local file system.
     *
     * @param repositoryUrl The repository URL or file path to convert, must not be {@code null}.
     * @return The resulting URI, never {@code null}.
     */
    public static URI toUri(String repositoryUrl) {
        final String protocol = protocol(repositoryUrl);
        if ("file".equals(protocol)) {
            // Parse as a proper file: URI so that percent-encoding and Windows paths like
            // file:/C:/... or file:///C:/... are handled correctly by the JDK.
            return Paths.get(URI.create(repositoryUrl)).toUri();
        } else if (protocol.isEmpty() || isWindowsDriveLetter(protocol)) {
            // Use Paths.get() to parse the local path lexically, without resolving against the JVM
            // working directory via File (which may be on a different drive on Windows).
            return Paths.get(basedir(repositoryUrl)).toUri();
        } else {
            return URI.create(repositoryUrl);
        }
    }

    /**
     * Return the protocol name.
     *
     * @param url the url
     * @return the protocol or empty string.
     */
    private static String protocol(final String url) {
        final int pos = url.indexOf(":");

        if (pos == -1) {
            return "";
        }
        return url.substring(0, pos).trim();
    }

    /**
     * Derive the path portion of the given URL.
     *
     * @param url the file-repository URL
     * @return the basedir of the repository
     */
    private static String basedir(String url) {
        String protocol = protocol(url);

        String retValue;

        if (!protocol.isEmpty()) {
            // A single letter (case-insensitive) is a Windows drive letter (e.g. "C:\path" or "c:\path"),
            // not a URL scheme. Keep it in the path; do not strip it as if it were a protocol prefix.
            if (isWindowsDriveLetter(protocol)) {
                retValue = url;
            } else {
                retValue = url.substring(protocol.length() + 1);
            }
        } else {
            retValue = url;
        }
        retValue = decode(retValue);
        // special case: if omitted // on protocol, keep path as is
        if (retValue.startsWith("//")) {
            retValue = retValue.substring(2);

            if (retValue.length() >= 2 && (retValue.charAt(1) == '|' || retValue.charAt(1) == ':')) {
                // special case: if there is a windows drive letter, then keep the original return value
                retValue = retValue.charAt(0) + ":" + retValue.substring(2);
            } else {
                // Now we expect the host
                int index = retValue.indexOf("/");
                if (index >= 0) {
                    retValue = retValue.substring(index + 1);
                }

                // special case: if there is a windows drive letter, then keep the original return value
                if (retValue.length() >= 2 && (retValue.charAt(1) == '|' || retValue.charAt(1) == ':')) {
                    retValue = retValue.charAt(0) + ":" + retValue.substring(2);
                } else if (index >= 0) {
                    // leading / was previously stripped
                    retValue = "/" + retValue;
                }
            }
        }

        // special case: if there is a windows drive letter using |, switch to :
        if (retValue.length() >= 2 && retValue.charAt(1) == '|') {
            retValue = retValue.charAt(0) + ":" + retValue.substring(2);
        }

        return retValue.trim();
    }

    /**
     * Returns {@code true} if the given protocol token is a single ASCII letter, which on Windows indicates
     * a drive letter (e.g. {@code C} in {@code C:\path} or {@code c} in {@code c:/path}) rather than a URL scheme.
     * The check is locale-independent.
     */
    private static boolean isWindowsDriveLetter(String protocol) {
        return protocol.length() == 1 && Character.isLetter(protocol.charAt(0));
    }

    /**
     * Decodes the specified (portion of a) URL. <strong>Note:</strong> This decoder assumes that ISO-8859-1 is used to
     * convert URL-encoded octets to characters.
     *
     * @param url The URL to decode, may be <code>null</code>.
     * @return The decoded URL or <code>null</code> if the input was <code>null</code>.
     */
    private static String decode(String url) {
        String decoded = url;
        if (url != null) {
            int pos = -1;
            while ((pos = decoded.indexOf('%', pos + 1)) >= 0) {
                if (pos + 2 < decoded.length()) {
                    String hexStr = decoded.substring(pos + 1, pos + 3);
                    char ch = (char) Integer.parseInt(hexStr, 16);
                    decoded = decoded.substring(0, pos) + ch + decoded.substring(pos + 3);
                }
            }
        }
        return decoded;
    }
}
