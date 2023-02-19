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
package org.eclipse.aether.util;

/**
 * A utility class to ease string processing.
 */
@Deprecated
public final class StringUtils {

    private StringUtils() {
        // hide constructor
    }

    /**
     * Checks whether a string is {@code null} or of zero length.
     *
     * @param string The string to check, may be {@code null}.
     * @return {@code true} if the string is {@code null} or of zero length, {@code false} otherwise.
     * @deprecated use {@code org.apache.commons.lang3.StringUtils.isEmpty()} instead
     */
    public static boolean isEmpty(String string) {
        return string == null || string.isEmpty();
    }
}
