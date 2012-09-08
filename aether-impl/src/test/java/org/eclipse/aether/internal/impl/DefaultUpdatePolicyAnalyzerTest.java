/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import static org.eclipse.aether.repository.RepositoryPolicy.*;
import static org.junit.Assert.*;

import java.util.Calendar;

import org.eclipse.aether.internal.test.impl.TestRepositorySystemSession;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class DefaultUpdatePolicyAnalyzerTest
{

    private DefaultUpdatePolicyAnalyzer analyzer;

    private TestRepositorySystemSession session;

    @Before
    public void setup()
        throws Exception
    {
        analyzer = new DefaultUpdatePolicyAnalyzer();
        session = new TestRepositorySystemSession();
    }

    private long now()
    {
        return System.currentTimeMillis();
    }

    @Test
    public void testIsUpdateRequired_PolicyNever()
        throws Exception
    {
        String policy = RepositoryPolicy.UPDATE_POLICY_NEVER;
        assertEquals( false, analyzer.isUpdatedRequired( session, Long.MIN_VALUE, policy ) );
        assertEquals( false, analyzer.isUpdatedRequired( session, Long.MAX_VALUE, policy ) );
        assertEquals( false, analyzer.isUpdatedRequired( session, 0, policy ) );
        assertEquals( false, analyzer.isUpdatedRequired( session, 1, policy ) );
        assertEquals( false, analyzer.isUpdatedRequired( session, now() - 604800000, policy ) );
    }

    @Test
    public void testIsUpdateRequired_PolicyAlways()
        throws Exception
    {
        String policy = RepositoryPolicy.UPDATE_POLICY_ALWAYS;
        assertEquals( true, analyzer.isUpdatedRequired( session, Long.MIN_VALUE, policy ) );
        assertEquals( true, analyzer.isUpdatedRequired( session, Long.MAX_VALUE, policy ) );
        assertEquals( true, analyzer.isUpdatedRequired( session, 0, policy ) );
        assertEquals( true, analyzer.isUpdatedRequired( session, 1, policy ) );
        assertEquals( true, analyzer.isUpdatedRequired( session, now() - 1000, policy ) );
    }

    @Test
    public void testIsUpdateRequired_PolicyDaily()
        throws Exception
    {
        Calendar cal = Calendar.getInstance();
        cal.set( Calendar.HOUR_OF_DAY, 0 );
        cal.set( Calendar.MINUTE, 0 );
        cal.set( Calendar.SECOND, 0 );
        cal.set( Calendar.MILLISECOND, 0 );
        long localMidnight = cal.getTimeInMillis();

        String policy = RepositoryPolicy.UPDATE_POLICY_DAILY;
        assertEquals( true, analyzer.isUpdatedRequired( session, Long.MIN_VALUE, policy ) );
        assertEquals( false, analyzer.isUpdatedRequired( session, Long.MAX_VALUE, policy ) );
        assertEquals( false, analyzer.isUpdatedRequired( session, localMidnight + 0, policy ) );
        assertEquals( false, analyzer.isUpdatedRequired( session, localMidnight + 1, policy ) );
        assertEquals( true, analyzer.isUpdatedRequired( session, localMidnight - 1, policy ) );
    }

    @Test
    public void testIsUpdateRequired_PolicyInterval()
        throws Exception
    {
        String policy = RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":5";
        assertEquals( true, analyzer.isUpdatedRequired( session, Long.MIN_VALUE, policy ) );
        assertEquals( false, analyzer.isUpdatedRequired( session, Long.MAX_VALUE, policy ) );
        assertEquals( false, analyzer.isUpdatedRequired( session, now(), policy ) );
        assertEquals( false, analyzer.isUpdatedRequired( session, now() - 5 - 1, policy ) );
        assertEquals( false, analyzer.isUpdatedRequired( session, now() - 1000 * 5 - 1, policy ) );
        assertEquals( true, analyzer.isUpdatedRequired( session, now() - 1000 * 60 * 5 - 1, policy ) );

        policy = RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":invalid";
        assertEquals( false, analyzer.isUpdatedRequired( session, now(), policy ) );
    }

    @Test
    public void testEffectivePolicy()
    {
        assertEquals( UPDATE_POLICY_ALWAYS,
                      analyzer.getEffectiveUpdatePolicy( session, UPDATE_POLICY_ALWAYS, UPDATE_POLICY_DAILY ) );
        assertEquals( UPDATE_POLICY_ALWAYS,
                      analyzer.getEffectiveUpdatePolicy( session, UPDATE_POLICY_ALWAYS, UPDATE_POLICY_NEVER ) );
        assertEquals( UPDATE_POLICY_DAILY,
                      analyzer.getEffectiveUpdatePolicy( session, UPDATE_POLICY_DAILY, UPDATE_POLICY_NEVER ) );
        assertEquals( UPDATE_POLICY_INTERVAL + ":60",
                      analyzer.getEffectiveUpdatePolicy( session, UPDATE_POLICY_DAILY, UPDATE_POLICY_INTERVAL + ":60" ) );
        assertEquals( UPDATE_POLICY_INTERVAL + ":60",
                      analyzer.getEffectiveUpdatePolicy( session, UPDATE_POLICY_INTERVAL + ":100",
                                                         UPDATE_POLICY_INTERVAL + ":60" ) );
    }

}
