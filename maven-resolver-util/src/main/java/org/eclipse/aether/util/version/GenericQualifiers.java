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

import java.util.Collections;
import java.util.LinkedHashMap;
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
        Map<String, Integer> qualifiers = new LinkedHashMap<>();
        qualifiers.put(LABEL_ALPHA, QUALIFIER_ALPHA);
        qualifiers.put(LABEL_BETA, QUALIFIER_BETA);
        qualifiers.put(LABEL_MILESTONE, QUALIFIER_MILESTONE);
        qualifiers.put("rc", QUALIFIER_RC);
        qualifiers.put("cr", QUALIFIER_RC);
        qualifiers.put("snapshot", QUALIFIER_SNAPSHOT);
        qualifiers.put("ga", QUALIFIER_ZERO);
        qualifiers.put("final", QUALIFIER_ZERO);
        qualifiers.put("release", QUALIFIER_ZERO);
        qualifiers.put("sp", QUALIFIER_SP);
        QUALIFIERS = Collections.unmodifiableMap(qualifiers);
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
                String label = entry.getKey();
                int pos = v.indexOf(label);
                if (pos > -1
                        && (pos == 0 || !Character.isLetter(v.charAt(pos - 1)))
                        && (pos >= v.length() - label.length()
                                || !Character.isLetter(v.charAt(pos + label.length())))) {
                    // it must be surrounded by "transition" (non-char; to avoid "rc" detection in "1.0-arc")
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
