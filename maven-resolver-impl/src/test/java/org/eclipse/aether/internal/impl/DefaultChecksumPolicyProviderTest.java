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

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.transfer.TransferResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultChecksumPolicyProviderTest {

    private static final String CHECKSUM_POLICY_UNKNOWN = "unknown";

    private DefaultRepositorySystemSession session;

    private DefaultChecksumPolicyProvider provider;

    private RemoteRepository repository;

    private TransferResource resource;

    @BeforeEach
    void setup() {
        session = TestUtils.newSession();
        provider = new DefaultChecksumPolicyProvider();
        repository = new RemoteRepository.Builder("test", "default", "file:/void").build();
        resource = new TransferResource(repository.getId(), repository.getUrl(), "file.txt", null, null, null);
    }

    @AfterEach
    void teardown() {
        provider = null;
        session = null;
        repository = null;
        resource = null;
    }

    @Test
    void testNewChecksumPolicy_Fail() {
        ChecksumPolicy policy =
                provider.newChecksumPolicy(session, repository, resource, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        assertNotNull(policy);
        assertEquals(FailChecksumPolicy.class, policy.getClass());
    }

    @Test
    void testNewChecksumPolicy_Warn() {
        ChecksumPolicy policy =
                provider.newChecksumPolicy(session, repository, resource, RepositoryPolicy.CHECKSUM_POLICY_WARN);
        assertNotNull(policy);
        assertEquals(WarnChecksumPolicy.class, policy.getClass());
    }

    @Test
    void testNewChecksumPolicy_Ignore() {
        ChecksumPolicy policy =
                provider.newChecksumPolicy(session, repository, resource, RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
        assertNull(policy);
    }

    @Test
    void testNewChecksumPolicy_Unknown() {
        assertThrows(
                IllegalArgumentException.class,
                () -> provider.newChecksumPolicy(session, repository, resource, CHECKSUM_POLICY_UNKNOWN));
    }

    @Test
    void testGetEffectiveChecksumPolicy_EqualPolicies() {
        String[] policies = {
            RepositoryPolicy.CHECKSUM_POLICY_FAIL,
            RepositoryPolicy.CHECKSUM_POLICY_WARN,
            RepositoryPolicy.CHECKSUM_POLICY_IGNORE
        };
        for (String policy : policies) {
            assertEquals(policy, provider.getEffectiveChecksumPolicy(session, policy, policy), policy);
        }
    }

    @Test
    void testGetEffectiveChecksumPolicy_DifferentPolicies() {
        String[][] testCases = {
            {RepositoryPolicy.CHECKSUM_POLICY_WARN, RepositoryPolicy.CHECKSUM_POLICY_FAIL},
            {RepositoryPolicy.CHECKSUM_POLICY_IGNORE, RepositoryPolicy.CHECKSUM_POLICY_FAIL},
            {RepositoryPolicy.CHECKSUM_POLICY_IGNORE, RepositoryPolicy.CHECKSUM_POLICY_WARN}
        };
        for (String[] testCase : testCases) {
            assertEquals(
                    testCase[0],
                    provider.getEffectiveChecksumPolicy(session, testCase[0], testCase[1]),
                    testCase[0] + " vs " + testCase[1]);
            assertEquals(
                    testCase[0],
                    provider.getEffectiveChecksumPolicy(session, testCase[1], testCase[0]),
                    testCase[0] + " vs " + testCase[1]);
        }
    }

    @Test
    void testGetEffectiveChecksumPolicy_UnknownPolicies() {
        String[][] testCases = {
            {RepositoryPolicy.CHECKSUM_POLICY_WARN, RepositoryPolicy.CHECKSUM_POLICY_FAIL},
            {RepositoryPolicy.CHECKSUM_POLICY_WARN, RepositoryPolicy.CHECKSUM_POLICY_WARN},
            {RepositoryPolicy.CHECKSUM_POLICY_IGNORE, RepositoryPolicy.CHECKSUM_POLICY_IGNORE}
        };
        for (String[] testCase : testCases) {
            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                    () -> provider.getEffectiveChecksumPolicy(session, CHECKSUM_POLICY_UNKNOWN, testCase[1]));
            assertEquals(e.getMessage(), "Unsupported policy: unknown");
            e = assertThrows(
                    IllegalArgumentException.class,
                    () -> provider.getEffectiveChecksumPolicy(session, testCase[1], CHECKSUM_POLICY_UNKNOWN));
            assertEquals(e.getMessage(), "Unsupported policy: unknown");
        }
    }
}
