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

import java.util.Calendar;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.eclipse.aether.repository.RepositoryPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class DefaultUpdatePolicyAnalyzerTest {

    private DefaultUpdatePolicyAnalyzer analyzer;

    private DefaultRepositorySystemSession session;

    @BeforeEach
    void setup() {
        analyzer = new DefaultUpdatePolicyAnalyzer();
        session = TestUtils.newSession();
    }

    private long now() {
        return System.currentTimeMillis();
    }

    @Test
    void testIsUpdateRequired_PolicyNever() {
        String policy = RepositoryPolicy.UPDATE_POLICY_NEVER;
        assertFalse(analyzer.isUpdatedRequired(session, Long.MIN_VALUE, policy));
        assertFalse(analyzer.isUpdatedRequired(session, Long.MAX_VALUE, policy));
        assertFalse(analyzer.isUpdatedRequired(session, 0, policy));
        assertFalse(analyzer.isUpdatedRequired(session, 1, policy));
        assertFalse(analyzer.isUpdatedRequired(session, now() - 604800000, policy));
    }

    @Test
    void testIsUpdateRequired_PolicyAlways() {
        String policy = RepositoryPolicy.UPDATE_POLICY_ALWAYS;
        assertTrue(analyzer.isUpdatedRequired(session, Long.MIN_VALUE, policy));
        assertTrue(analyzer.isUpdatedRequired(session, Long.MAX_VALUE, policy));
        assertTrue(analyzer.isUpdatedRequired(session, 0, policy));
        assertTrue(analyzer.isUpdatedRequired(session, 1, policy));
        assertTrue(analyzer.isUpdatedRequired(session, now() - 1000, policy));
    }

    @Test
    void testIsUpdateRequired_PolicyDaily() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long localMidnight = cal.getTimeInMillis();

        String policy = RepositoryPolicy.UPDATE_POLICY_DAILY;
        assertTrue(analyzer.isUpdatedRequired(session, Long.MIN_VALUE, policy));
        assertFalse(analyzer.isUpdatedRequired(session, Long.MAX_VALUE, policy));
        assertFalse(analyzer.isUpdatedRequired(session, localMidnight, policy));
        assertFalse(analyzer.isUpdatedRequired(session, localMidnight + 1, policy));
        assertTrue(analyzer.isUpdatedRequired(session, localMidnight - 1, policy));
    }

    @Test
    void testIsUpdateRequired_PolicyInterval() {
        String policy = RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":5";
        assertTrue(analyzer.isUpdatedRequired(session, Long.MIN_VALUE, policy));
        assertFalse(analyzer.isUpdatedRequired(session, Long.MAX_VALUE, policy));
        assertFalse(analyzer.isUpdatedRequired(session, now(), policy));
        assertFalse(analyzer.isUpdatedRequired(session, now() - 5 - 1, policy));
        assertFalse(analyzer.isUpdatedRequired(session, now() - 1000 * 5 - 1, policy));
        assertTrue(analyzer.isUpdatedRequired(session, now() - 1000 * 60 * 5 - 1, policy));

        policy = RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":invalid";
        assertFalse(analyzer.isUpdatedRequired(session, now(), policy));
    }

    @Test
    void testEffectivePolicy() {
        assertEquals(
                UPDATE_POLICY_ALWAYS,
                analyzer.getEffectiveUpdatePolicy(session, UPDATE_POLICY_ALWAYS, UPDATE_POLICY_DAILY));
        assertEquals(
                UPDATE_POLICY_ALWAYS,
                analyzer.getEffectiveUpdatePolicy(session, UPDATE_POLICY_ALWAYS, UPDATE_POLICY_NEVER));
        assertEquals(
                UPDATE_POLICY_DAILY,
                analyzer.getEffectiveUpdatePolicy(session, UPDATE_POLICY_DAILY, UPDATE_POLICY_NEVER));
        assertEquals(
                UPDATE_POLICY_INTERVAL + ":60",
                analyzer.getEffectiveUpdatePolicy(session, UPDATE_POLICY_DAILY, UPDATE_POLICY_INTERVAL + ":60"));
        assertEquals(
                UPDATE_POLICY_INTERVAL + ":60",
                analyzer.getEffectiveUpdatePolicy(
                        session, UPDATE_POLICY_INTERVAL + ":100", UPDATE_POLICY_INTERVAL + ":60"));
    }
}
