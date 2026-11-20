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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultChecksumPolicyProviderTest {

    private static final String CHECKSUM_POLICY_UNKNOWN = "unknown";

    private DefaultRepositorySystemSession session;

    private DefaultChecksumPolicyProvider provider;

    private RemoteRepository repository;

    private TransferResource resource;

    @BeforeEach
    public void setup() {
        session = TestUtils.newSession();
        provider = new DefaultChecksumPolicyProvider();
        repository = new RemoteRepository.Builder("test", "default", "file:/void").build();
        resource = new TransferResource(repository.getId(), repository.getUrl(), "file.txt", null, null);
    }

    @AfterEach
    public void teardown() {
        provider = null;
        session = null;
        repository = null;
        resource = null;
    }

    @Test
    public void testNewChecksumPolicyFail() {
        ChecksumPolicy policy =
                provider.newChecksumPolicy(session, repository, resource, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        assertNotNull(policy);
        assertEquals(FailChecksumPolicy.class, policy.getClass());
    }

    @Test
    public void testNewChecksumPolicyWarn() {
        ChecksumPolicy policy =
                provider.newChecksumPolicy(session, repository, resource, RepositoryPolicy.CHECKSUM_POLICY_WARN);
        assertNotNull(policy);
        assertEquals(WarnChecksumPolicy.class, policy.getClass());
    }

    @Test
    public void testNewChecksumPolicyIgnore() {
        ChecksumPolicy policy =
                provider.newChecksumPolicy(session, repository, resource, RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
        assertNull(policy);
    }

    @Test
    public void testNewChecksumPolicyUnknown() {
        assertThrows(
                IllegalArgumentException.class,
                () -> provider.newChecksumPolicy(session, repository, resource, CHECKSUM_POLICY_UNKNOWN));
    }

    @Test
    public void testGetEffectiveChecksumPolicyEqualPolicies() {
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
    public void testGetEffectiveChecksumPolicyDifferentPolicies() {
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
    public void testGetEffectiveChecksumPolicyUnknownPolicies() {
        String[][] testCases = {
            {RepositoryPolicy.CHECKSUM_POLICY_WARN, RepositoryPolicy.CHECKSUM_POLICY_FAIL},
            {RepositoryPolicy.CHECKSUM_POLICY_WARN, RepositoryPolicy.CHECKSUM_POLICY_WARN},
            {RepositoryPolicy.CHECKSUM_POLICY_IGNORE, RepositoryPolicy.CHECKSUM_POLICY_IGNORE}
        };
        for (String[] testCase : testCases) {
            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                    () -> provider.getEffectiveChecksumPolicy(session, CHECKSUM_POLICY_UNKNOWN, testCase[1]));
            assertEquals("Unsupported policy: unknown", e.getMessage());
            e = assertThrows(
                    IllegalArgumentException.class,
                    () -> provider.getEffectiveChecksumPolicy(session, testCase[1], CHECKSUM_POLICY_UNKNOWN));
            assertEquals("Unsupported policy: unknown", e.getMessage());
        }
    }
}
