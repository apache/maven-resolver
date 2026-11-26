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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.aether.version.Version;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Runs the language-independent version comparison cases from
 * {@code version-comparison-cases.tsv} against {@link GenericVersion}.
 *
 * <p>The data file is intentionally plain text: other version schemes (Maven core's
 * ComparableVersion, Gradle, semver libraries, ...) can consume the same cases without
 * depending on this test suite.</p>
 */
class VersionComparisonDataTest extends AbstractVersionTest {

    private static final String DATA = "/version-comparison-cases.tsv";

    @Override
    protected Version newVersion(String version) {
        return new GenericVersion(version);
    }

    static Stream<Arguments> cases() throws IOException {
        List<Arguments> result = new ArrayList<>();

        try (InputStream in = VersionComparisonDataTest.class.getResourceAsStream(DATA)) {
            assertNotNull(in, "Missing test data " + DATA);

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Keep empty fields: an empty version string is a valid case.
                String[] parts = line.split("\t", -1);
                assertEquals(3, parts.length, "Invalid data on line " + lineNumber);

                int expected;
                if ("LT".equals(parts[2])) {
                    expected = X_LT_Y;
                } else if ("EQ".equals(parts[2])) {
                    expected = X_EQ_Y;
                } else if ("GT".equals(parts[2])) {
                    expected = X_GT_Y;
                } else {
                    throw new IllegalArgumentException("Invalid expectation '" + parts[2] + "' on line " + lineNumber);
                }

                result.add(Arguments.of(expected, parts[0], parts[1]));
            }
        }

        return result.stream();
    }

    @ParameterizedTest(name = "{1} vs {2} => {0}")
    @MethodSource("cases")
    void comparesVersionPairs(int expected, String left, String right) {
        assertOrder(expected, left, right);
    }
}
