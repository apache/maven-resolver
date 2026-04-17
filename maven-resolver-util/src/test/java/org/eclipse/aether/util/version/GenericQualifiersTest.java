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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class GenericQualifiersTest {
    private static final int NO_QUALIFIER = Integer.MIN_VALUE;

    @Test
    void fullCases() {
        assertEquals(
                GenericQualifiers.QUALIFIER_ALPHA,
                GenericQualifiers.qualifier("1-alpha-1").orElse(NO_QUALIFIER));
        assertEquals(
                GenericQualifiers.QUALIFIER_ALPHA,
                GenericQualifiers.qualifier("alpha-1").orElse(NO_QUALIFIER));
        assertEquals(
                GenericQualifiers.QUALIFIER_ALPHA,
                GenericQualifiers.qualifier("1-alpha").orElse(NO_QUALIFIER));
        assertEquals(
                GenericQualifiers.QUALIFIER_BETA,
                GenericQualifiers.qualifier("1-beta").orElse(NO_QUALIFIER));
        assertEquals(
                GenericQualifiers.QUALIFIER_MILESTONE,
                GenericQualifiers.qualifier("milestone").orElse(NO_QUALIFIER));
        assertEquals(
                GenericQualifiers.QUALIFIER_MILESTONE,
                GenericQualifiers.qualifier("1-milestone").orElse(NO_QUALIFIER));
        assertEquals(
                GenericQualifiers.QUALIFIER_ZERO,
                GenericQualifiers.qualifier("1-ga-1").orElse(NO_QUALIFIER));
        assertEquals(
                GenericQualifiers.QUALIFIER_ZERO,
                GenericQualifiers.qualifier("1-final").orElse(NO_QUALIFIER));
        assertEquals(
                GenericQualifiers.QUALIFIER_SP,
                GenericQualifiers.qualifier("1-sp").orElse(NO_QUALIFIER));
    }

    @Test
    void shortCases() {
        // special: alpha, beta and milestone are detected also when in form of "a1" (a, b, or m immediately followed by
        // number)
        assertEquals(
                GenericQualifiers.QUALIFIER_ALPHA,
                GenericQualifiers.qualifier("1-a1").orElse(NO_QUALIFIER));
        assertEquals(
                GenericQualifiers.QUALIFIER_ALPHA,
                GenericQualifiers.qualifier("a1").orElse(NO_QUALIFIER));
        assertEquals(
                GenericQualifiers.QUALIFIER_BETA,
                GenericQualifiers.qualifier("1-b1").orElse(NO_QUALIFIER));
        assertEquals(
                GenericQualifiers.QUALIFIER_MILESTONE,
                GenericQualifiers.qualifier("1-m1").orElse(NO_QUALIFIER));
        assertEquals(
                GenericQualifiers.QUALIFIER_MILESTONE,
                GenericQualifiers.qualifier("m1").orElse(NO_QUALIFIER));
    }

    @Test
    void edgeCases() {
        assertFalse(GenericQualifiers.qualifier("1.0-arced").isPresent()); // rc
        assertFalse(GenericQualifiers.qualifier("1.0-legacy").isPresent()); // ga
        assertFalse(GenericQualifiers.qualifier("1.0-sacred").isPresent()); // cr
    }
}
