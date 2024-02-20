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

import java.io.File;
import java.net.URI;

/**
 * URL handling for file URLs. Based on org.apache.maven.wagon.PathUtils.
 *
 * @since 2.0.0
 */
public final class RepositoryUriUtils {

    private RepositoryUriUtils() {}

    public static URI toUri(String repositoryUrl) {
        final String protocol = protocol(repositoryUrl);
        if ("file".equals(protocol)
                || protocol.isEmpty()
                || protocol.length() == 1
                        && (Character.isLetter(protocol.charAt(0)) && Character.isUpperCase(protocol.charAt(0)))) {
            return new File(basedir(repositoryUrl)).toURI();
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
            retValue = url.substring(protocol.length() + 1);
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
