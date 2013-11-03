/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import static org.junit.Assert.*;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.transfer.TransferResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DefaultChecksumPolicyProviderTest
{

    private static final String CHECKSUM_POLICY_UNKNOWN = "unknown";

    private DefaultRepositorySystemSession session;

    private DefaultChecksumPolicyProvider provider;

    private RemoteRepository repository;

    private TransferResource resource;

    @Before
    public void setup()
        throws Exception
    {
        session = TestUtils.newSession();
        provider = new DefaultChecksumPolicyProvider();
        repository = new RemoteRepository.Builder( "test", "default", "file:/void" ).build();
        resource = new TransferResource( repository.getUrl(), "file.txt", null, null );
    }

    @After
    public void teardown()
        throws Exception
    {
        provider = null;
        session = null;
        repository = null;
        resource = null;
    }

    @Test
    public void testNewChecksumPolicy_Fail()
    {
        ChecksumPolicy policy =
            provider.newChecksumPolicy( session, repository, resource, RepositoryPolicy.CHECKSUM_POLICY_FAIL );
        assertNotNull( policy );
        assertEquals( FailChecksumPolicy.class, policy.getClass() );
    }

    @Test
    public void testNewChecksumPolicy_Warn()
    {
        ChecksumPolicy policy =
            provider.newChecksumPolicy( session, repository, resource, RepositoryPolicy.CHECKSUM_POLICY_WARN );
        assertNotNull( policy );
        assertEquals( WarnChecksumPolicy.class, policy.getClass() );
    }

    @Test
    public void testNewChecksumPolicy_Ignore()
    {
        ChecksumPolicy policy =
            provider.newChecksumPolicy( session, repository, resource, RepositoryPolicy.CHECKSUM_POLICY_IGNORE );
        assertNull( policy );
    }

    @Test
    public void testNewChecksumPolicy_Unknown()
    {
        ChecksumPolicy policy = provider.newChecksumPolicy( session, repository, resource, CHECKSUM_POLICY_UNKNOWN );
        assertNotNull( policy );
        assertEquals( WarnChecksumPolicy.class, policy.getClass() );
    }

    @Test
    public void testGetEffectiveChecksumPolicy_EqualPolicies()
    {
        String[] policies =
            { RepositoryPolicy.CHECKSUM_POLICY_FAIL, RepositoryPolicy.CHECKSUM_POLICY_WARN,
                RepositoryPolicy.CHECKSUM_POLICY_IGNORE, CHECKSUM_POLICY_UNKNOWN };
        for ( String policy : policies )
        {
            assertEquals( policy, policy, provider.getEffectiveChecksumPolicy( session, policy, policy ) );
        }
    }

    @Test
    public void testGetEffectiveChecksumPolicy_DifferentPolicies()
    {
        String[][] testCases =
            { { RepositoryPolicy.CHECKSUM_POLICY_WARN, RepositoryPolicy.CHECKSUM_POLICY_FAIL },
                { RepositoryPolicy.CHECKSUM_POLICY_IGNORE, RepositoryPolicy.CHECKSUM_POLICY_FAIL },
                { RepositoryPolicy.CHECKSUM_POLICY_IGNORE, RepositoryPolicy.CHECKSUM_POLICY_WARN } };
        for ( String[] testCase : testCases )
        {
            assertEquals( testCase[0] + " vs " + testCase[1], testCase[0],
                          provider.getEffectiveChecksumPolicy( session, testCase[0], testCase[1] ) );
            assertEquals( testCase[0] + " vs " + testCase[1], testCase[0],
                          provider.getEffectiveChecksumPolicy( session, testCase[1], testCase[0] ) );
        }
    }

    @Test
    public void testGetEffectiveChecksumPolicy_UnknownPolicies()
    {
        String[][] testCases =
            { { RepositoryPolicy.CHECKSUM_POLICY_WARN, RepositoryPolicy.CHECKSUM_POLICY_FAIL },
                { RepositoryPolicy.CHECKSUM_POLICY_WARN, RepositoryPolicy.CHECKSUM_POLICY_WARN },
                { RepositoryPolicy.CHECKSUM_POLICY_IGNORE, RepositoryPolicy.CHECKSUM_POLICY_IGNORE } };
        for ( String[] testCase : testCases )
        {
            assertEquals( "unknown vs " + testCase[1], testCase[0],
                          provider.getEffectiveChecksumPolicy( session, CHECKSUM_POLICY_UNKNOWN, testCase[1] ) );
            assertEquals( "unknown vs " + testCase[1], testCase[0],
                          provider.getEffectiveChecksumPolicy( session, testCase[1], CHECKSUM_POLICY_UNKNOWN ) );
        }
    }

}
