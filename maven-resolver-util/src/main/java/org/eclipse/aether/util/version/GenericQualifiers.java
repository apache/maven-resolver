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
package org.eclipse.aether.util.version;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The recognized generic version qualifiers. Qualifiers may apply a "shift" to versions (ie when sorting), if present.
 *
 * @since 2.0.17
 */
public final class GenericQualifiers {
    private GenericQualifiers() {}

    public static final String LABEL_ALPHA = "alpha";

    public static final String LABEL_BETA = "beta";

    public static final String LABEL_MILESTONE = "milestone";

    public static final Integer QUALIFIER_ALPHA = -5;

    public static final Integer QUALIFIER_BETA = -4;

    public static final Integer QUALIFIER_MILESTONE = -3;

    public static final Integer QUALIFIER_RC = -2;

    public static final Integer QUALIFIER_SNAPSHOT = -1;

    public static final Integer QUALIFIER_ZERO = 0;

    public static final Integer QUALIFIER_SP = 1;

    private static final Map<String, Integer> QUALIFIERS;

    static {
        QUALIFIERS = new HashMap<>();
        QUALIFIERS.put(LABEL_ALPHA, QUALIFIER_ALPHA);
        QUALIFIERS.put(LABEL_BETA, QUALIFIER_BETA);
        QUALIFIERS.put(LABEL_MILESTONE, QUALIFIER_MILESTONE);
        QUALIFIERS.put("rc", QUALIFIER_RC);
        QUALIFIERS.put("cr", QUALIFIER_RC);
        QUALIFIERS.put("snapshot", QUALIFIER_SNAPSHOT);
        QUALIFIERS.put("ga", QUALIFIER_ZERO);
        QUALIFIERS.put("final", QUALIFIER_ZERO);
        QUALIFIERS.put("release", QUALIFIER_ZERO);
        QUALIFIERS.put("", QUALIFIER_ZERO); // TODO: is this entry valid?
        QUALIFIERS.put("sp", QUALIFIER_SP);
    }

    /**
     * Returns qualifier (an {@link Integer} for given token, if detected. This method is used in {@link GenericVersion}
     * that tokenizes version, and uses string token in call. The input must have {@link String#toLowerCase(Locale)}
     * applied.
     */
    static Optional<Integer> tokenQualifier(String token) {
        return Optional.ofNullable(QUALIFIERS.get(token));
    }

    /**
     * Returns qualifier (an {@link Integer} for given string, if detected. Qualifier, if present, defines "shift",
     * if negative, it is a "preview version" (e.g. alpha, beta, milestone, release candidate or snapshot), if zero,
     * it is a "final version" (e.g. ga, final, release), if positive, it is a "service pack" version (e.g. sp).
     * If no qualifier is detected, an empty optional is returned.
     *
     * @param token the string to analyze for qualifier, must not be {@code null}
     */
    public static Optional<Integer> qualifier(String token) {
        // most trivial preview version is "a1"
        if (token.length() > 1) {
            String v = token.toLowerCase(Locale.ENGLISH);
            // simple case: full qualifier label is present (assuming once)
            for (Map.Entry<String, Integer> entry : QUALIFIERS.entrySet()) {
                // TODO on map we have empty string "" as key
                if (!entry.getKey().isEmpty() && v.contains(entry.getKey())) {
                    return Optional.of(entry.getValue());
                }
            }
            // complex case: contains 'a', 'b' or 'm' followed immediately by number
            for (char ch : new char[] {'a', 'b', 'm'}) {
                int idx = v.lastIndexOf(ch);
                if (idx > -1 && v.length() > idx + 1) {
                    if (Character.isDigit(v.charAt(idx + 1))) {
                        if (ch == 'a') {
                            return Optional.of(QUALIFIER_ALPHA);
                        } else if (ch == 'b') {
                            return Optional.of(QUALIFIER_BETA);
                        } else {
                            return Optional.of(QUALIFIER_MILESTONE);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
}
