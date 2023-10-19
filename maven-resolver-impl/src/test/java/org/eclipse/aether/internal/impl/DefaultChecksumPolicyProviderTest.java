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
import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class DefaultChecksumPolicyProviderTest {

    private static final String CHECKSUM_POLICY_UNKNOWN = "unknown";

    private DefaultRepositorySystemSession session;

    private DefaultChecksumPolicyProvider provider;

    private RemoteRepository repository;

    private TransferResource resource;

    @Before
    public void setup() {
        session = TestUtils.newSession();
        provider = new DefaultChecksumPolicyProvider();
        repository = new RemoteRepository.Builder("test", "default", "file:/void").build();
        resource = new TransferResource(repository.getId(), repository.getUrl(), "file.txt", null, null);
    }

    @After
    public void teardown() {
        provider = null;
        session = null;
        repository = null;
        resource = null;
    }

    @Test
    public void testNewChecksumPolicy_Fail() {
        ChecksumPolicy policy =
                provider.newChecksumPolicy(session, repository, resource, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        assertNotNull(policy);
        assertEquals(FailChecksumPolicy.class, policy.getClass());
    }

    @Test
    public void testNewChecksumPolicy_Warn() {
        ChecksumPolicy policy =
                provider.newChecksumPolicy(session, repository, resource, RepositoryPolicy.CHECKSUM_POLICY_WARN);
        assertNotNull(policy);
        assertEquals(WarnChecksumPolicy.class, policy.getClass());
    }

    @Test
    public void testNewChecksumPolicy_Ignore() {
        ChecksumPolicy policy =
                provider.newChecksumPolicy(session, repository, resource, RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
        assertNull(policy);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNewChecksumPolicy_Unknown() {
        ChecksumPolicy policy = provider.newChecksumPolicy(session, repository, resource, CHECKSUM_POLICY_UNKNOWN);
        assertNotNull(policy);
        assertEquals(WarnChecksumPolicy.class, policy.getClass());
    }

    @Test
    public void testGetEffectiveChecksumPolicy_EqualPolicies() {
        String[] policies = {
            RepositoryPolicy.CHECKSUM_POLICY_FAIL,
            RepositoryPolicy.CHECKSUM_POLICY_WARN,
            RepositoryPolicy.CHECKSUM_POLICY_IGNORE
        };
        for (String policy : policies) {
            assertEquals(policy, policy, provider.getEffectiveChecksumPolicy(session, policy, policy));
        }
    }

    @Test
    public void testGetEffectiveChecksumPolicy_DifferentPolicies() {
        String[][] testCases = {
            {RepositoryPolicy.CHECKSUM_POLICY_WARN, RepositoryPolicy.CHECKSUM_POLICY_FAIL},
            {RepositoryPolicy.CHECKSUM_POLICY_IGNORE, RepositoryPolicy.CHECKSUM_POLICY_FAIL},
            {RepositoryPolicy.CHECKSUM_POLICY_IGNORE, RepositoryPolicy.CHECKSUM_POLICY_WARN}
        };
        for (String[] testCase : testCases) {
            assertEquals(
                    testCase[0] + " vs " + testCase[1],
                    testCase[0],
                    provider.getEffectiveChecksumPolicy(session, testCase[0], testCase[1]));
            assertEquals(
                    testCase[0] + " vs " + testCase[1],
                    testCase[0],
                    provider.getEffectiveChecksumPolicy(session, testCase[1], testCase[0]));
        }
    }

    @Test
    public void testGetEffectiveChecksumPolicy_UnknownPolicies() {
        String[][] testCases = {
            {RepositoryPolicy.CHECKSUM_POLICY_WARN, RepositoryPolicy.CHECKSUM_POLICY_FAIL},
            {RepositoryPolicy.CHECKSUM_POLICY_WARN, RepositoryPolicy.CHECKSUM_POLICY_WARN},
            {RepositoryPolicy.CHECKSUM_POLICY_IGNORE, RepositoryPolicy.CHECKSUM_POLICY_IGNORE}
        };
        for (String[] testCase : testCases) {
            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                    () -> provider.getEffectiveChecksumPolicy(session, CHECKSUM_POLICY_UNKNOWN, testCase[1]));
            assertThat(e.getMessage(), is("Unsupported policy: unknown"));
            e = assertThrows(
                    IllegalArgumentException.class,
                    () -> provider.getEffectiveChecksumPolicy(session, testCase[1], CHECKSUM_POLICY_UNKNOWN));
            assertThat(e.getMessage(), is("Unsupported policy: unknown"));
        }
    }
}
