/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.util.version;

import java.util.Locale;

import org.eclipse.aether.util.version.GenericVersion;
import org.eclipse.aether.version.Version;
import org.junit.Test;

/**
 */
public class GenericVersionTest
    extends AbstractVersionTest
{

    protected Version newVersion( String version )
    {
        return new GenericVersion( version );
    }

    @Test
    public void testEmptyVersion()
    {
        assertOrder( X_EQ_Y, "0", "" );
    }

    @Test
    public void testNumericOrdering()
    {
        assertOrder( X_LT_Y, "2", "10" );
        assertOrder( X_LT_Y, "1.2", "1.10" );
        assertOrder( X_LT_Y, "1.0.2", "1.0.10" );
        assertOrder( X_LT_Y, "1.0.0.2", "1.0.0.10" );
        assertOrder( X_LT_Y, "1.0.20101206.111434.1", "1.0.20101206.111435.1" );
        assertOrder( X_LT_Y, "1.0.20101206.111434.2", "1.0.20101206.111434.10" );
    }

    @Test
    public void testDelimiters()
    {
        assertOrder( X_EQ_Y, "1.0", "1-0" );
        assertOrder( X_EQ_Y, "1.0", "1_0" );
        assertOrder( X_EQ_Y, "1.a", "1a" );
    }

    @Test
    public void testLeadingZerosAreSemanticallyIrrelevant()
    {
        assertOrder( X_EQ_Y, "1", "01" );
        assertOrder( X_EQ_Y, "1.2", "1.002" );
        assertOrder( X_EQ_Y, "1.2.3", "1.2.0003" );
        assertOrder( X_EQ_Y, "1.2.3.4", "1.2.3.00004" );
    }

    @Test
    public void testTrailingZerosAreSemanticallyIrrelevant()
    {
        assertOrder( X_EQ_Y, "1", "1.0.0.0.0.0.0.0.0.0.0.0.0.0" );
        assertOrder( X_EQ_Y, "1", "1-0-0-0-0-0-0-0-0-0-0-0-0-0" );
        assertOrder( X_EQ_Y, "1", "1.0-0.0-0.0-0.0-0.0-0.0-0.0" );
        assertOrder( X_EQ_Y, "1", "1.0000000000000" );
        assertOrder( X_EQ_Y, "1.0", "1.0.0" );
    }

    @Test
    public void testTrailingZerosBeforeQualifierAreSemanticallyIrrelevant()
    {
        assertOrder( X_EQ_Y, "1.0-ga", "1.0.0-ga" );
        assertOrder( X_EQ_Y, "1.0.ga", "1.0.0.ga" );
        assertOrder( X_EQ_Y, "1.0ga", "1.0.0ga" );

        assertOrder( X_EQ_Y, "1.0-alpha", "1.0.0-alpha" );
        assertOrder( X_EQ_Y, "1.0.alpha", "1.0.0.alpha" );
        assertOrder( X_EQ_Y, "1.0alpha", "1.0.0alpha" );
        assertOrder( X_EQ_Y, "1.0-alpha-snapshot", "1.0.0-alpha-snapshot" );
        assertOrder( X_EQ_Y, "1.0.alpha.snapshot", "1.0.0.alpha.snapshot" );

        assertOrder( X_EQ_Y, "1.x.0-alpha", "1.x.0.0-alpha" );
        assertOrder( X_EQ_Y, "1.x.0.alpha", "1.x.0.0.alpha" );
        assertOrder( X_EQ_Y, "1.x.0-alpha-snapshot", "1.x.0.0-alpha-snapshot" );
        assertOrder( X_EQ_Y, "1.x.0.alpha.snapshot", "1.x.0.0.alpha.snapshot" );
    }

    @Test
    public void testTrailingDelimitersAreSemanticallyIrrelevant()
    {
        assertOrder( X_EQ_Y, "1", "1............." );
        assertOrder( X_EQ_Y, "1", "1-------------" );
        assertOrder( X_EQ_Y, "1.0", "1............." );
        assertOrder( X_EQ_Y, "1.0", "1-------------" );
    }

    @Test
    public void testInitialDelimiters()
    {
        assertOrder( X_EQ_Y, "0.1", ".1" );
        assertOrder( X_EQ_Y, "0.0.1", "..1" );
        assertOrder( X_EQ_Y, "0.1", "-1" );
        assertOrder( X_EQ_Y, "0.0.1", "--1" );
    }

    @Test
    public void testConsecutiveDelimiters()
    {
        assertOrder( X_EQ_Y, "1.0.1", "1..1" );
        assertOrder( X_EQ_Y, "1.0.0.1", "1...1" );
        assertOrder( X_EQ_Y, "1.0.1", "1--1" );
        assertOrder( X_EQ_Y, "1.0.0.1", "1---1" );
    }

    @Test
    public void testUnlimitedNumberOfVersionComponents()
    {
        assertOrder( X_GT_Y, "1.0.1.2.3.4.5.6.7.8.9.0.1.2.10", "1.0.1.2.3.4.5.6.7.8.9.0.1.2.3" );
    }

    @Test
    public void testUnlimitedNumberOfDigitsInNumericComponent()
    {
        assertOrder( X_GT_Y, "1.1234567890123456789012345678901", "1.123456789012345678901234567891" );
    }

    @Test
    public void testTransitionFromDigitToLetterAndViceVersaIsEqualivantToDelimiter()
    {
        assertOrder( X_EQ_Y, "1alpha10", "1.alpha.10" );
        assertOrder( X_EQ_Y, "1alpha10", "1-alpha-10" );

        assertOrder( X_GT_Y, "1.alpha10", "1.alpha2" );
        assertOrder( X_GT_Y, "10alpha", "1alpha" );
    }

    @Test
    public void testWellKnownQualifierOrdering()
    {
        assertOrder( X_EQ_Y, "1-alpha1", "1-a1" );
        assertOrder( X_LT_Y, "1-alpha", "1-beta" );
        assertOrder( X_EQ_Y, "1-beta1", "1-b1" );
        assertOrder( X_LT_Y, "1-beta", "1-milestone" );
        assertOrder( X_EQ_Y, "1-milestone1", "1-m1" );
        assertOrder( X_LT_Y, "1-milestone", "1-rc" );
        assertOrder( X_EQ_Y, "1-rc", "1-cr" );
        assertOrder( X_LT_Y, "1-rc", "1-snapshot" );
        assertOrder( X_LT_Y, "1-snapshot", "1" );
        assertOrder( X_EQ_Y, "1", "1-ga" );
        assertOrder( X_EQ_Y, "1", "1.ga.0.ga" );
        assertOrder( X_EQ_Y, "1.0", "1-ga" );
        assertOrder( X_EQ_Y, "1", "1-ga.ga" );
        assertOrder( X_EQ_Y, "1", "1-ga-ga" );
        assertOrder( X_EQ_Y, "A", "A.ga.ga" );
        assertOrder( X_EQ_Y, "A", "A-ga-ga" );
        assertOrder( X_EQ_Y, "1", "1-final" );
        assertOrder( X_LT_Y, "1", "1-sp" );

        assertOrder( X_LT_Y, "A.rc.1", "A.ga.1" );
        assertOrder( X_GT_Y, "A.sp.1", "A.ga.1" );
        assertOrder( X_LT_Y, "A.rc.x", "A.ga.x" );
        assertOrder( X_GT_Y, "A.sp.x", "A.ga.x" );
    }

    @Test
    public void testWellKnownQualifierVersusUnknownQualifierOrdering()
    {
        assertOrder( X_GT_Y, "1-abc", "1-alpha" );
        assertOrder( X_GT_Y, "1-abc", "1-beta" );
        assertOrder( X_GT_Y, "1-abc", "1-milestone" );
        assertOrder( X_GT_Y, "1-abc", "1-rc" );
        assertOrder( X_GT_Y, "1-abc", "1-snapshot" );
        assertOrder( X_GT_Y, "1-abc", "1" );
        assertOrder( X_GT_Y, "1-abc", "1-sp" );
    }

    @Test
    public void testWellKnownSingleCharQualifiersOnlyRecognizedIfImmediatelyFollowedByNumber()
    {
        assertOrder( X_GT_Y, "1.0a", "1.0" );
        assertOrder( X_GT_Y, "1.0-a", "1.0" );
        assertOrder( X_GT_Y, "1.0.a", "1.0" );
        assertOrder( X_GT_Y, "1.0b", "1.0" );
        assertOrder( X_GT_Y, "1.0-b", "1.0" );
        assertOrder( X_GT_Y, "1.0.b", "1.0" );
        assertOrder( X_GT_Y, "1.0m", "1.0" );
        assertOrder( X_GT_Y, "1.0-m", "1.0" );
        assertOrder( X_GT_Y, "1.0.m", "1.0" );

        assertOrder( X_LT_Y, "1.0a1", "1.0" );
        assertOrder( X_LT_Y, "1.0-a1", "1.0" );
        assertOrder( X_LT_Y, "1.0.a1", "1.0" );
        assertOrder( X_LT_Y, "1.0b1", "1.0" );
        assertOrder( X_LT_Y, "1.0-b1", "1.0" );
        assertOrder( X_LT_Y, "1.0.b1", "1.0" );
        assertOrder( X_LT_Y, "1.0m1", "1.0" );
        assertOrder( X_LT_Y, "1.0-m1", "1.0" );
        assertOrder( X_LT_Y, "1.0.m1", "1.0" );

        assertOrder( X_GT_Y, "1.0a.1", "1.0" );
        assertOrder( X_GT_Y, "1.0a-1", "1.0" );
        assertOrder( X_GT_Y, "1.0b.1", "1.0" );
        assertOrder( X_GT_Y, "1.0b-1", "1.0" );
        assertOrder( X_GT_Y, "1.0m.1", "1.0" );
        assertOrder( X_GT_Y, "1.0m-1", "1.0" );
    }

    @Test
    public void testUnknownQualifierOrdering()
    {
        assertOrder( X_LT_Y, "1-abc", "1-abcd" );
        assertOrder( X_LT_Y, "1-abc", "1-bcd" );
        assertOrder( X_GT_Y, "1-abc", "1-aac" );
    }

    @Test
    public void testCaseInsensitiveOrderingOfQualifiers()
    {
        assertOrder( X_EQ_Y, "1.alpha", "1.ALPHA" );
        assertOrder( X_EQ_Y, "1.alpha", "1.Alpha" );

        assertOrder( X_EQ_Y, "1.beta", "1.BETA" );
        assertOrder( X_EQ_Y, "1.beta", "1.Beta" );

        assertOrder( X_EQ_Y, "1.milestone", "1.MILESTONE" );
        assertOrder( X_EQ_Y, "1.milestone", "1.Milestone" );

        assertOrder( X_EQ_Y, "1.rc", "1.RC" );
        assertOrder( X_EQ_Y, "1.rc", "1.Rc" );
        assertOrder( X_EQ_Y, "1.cr", "1.CR" );
        assertOrder( X_EQ_Y, "1.cr", "1.Cr" );

        assertOrder( X_EQ_Y, "1.snapshot", "1.SNAPSHOT" );
        assertOrder( X_EQ_Y, "1.snapshot", "1.Snapshot" );

        assertOrder( X_EQ_Y, "1.ga", "1.GA" );
        assertOrder( X_EQ_Y, "1.ga", "1.Ga" );
        assertOrder( X_EQ_Y, "1.final", "1.FINAL" );
        assertOrder( X_EQ_Y, "1.final", "1.Final" );

        assertOrder( X_EQ_Y, "1.sp", "1.SP" );
        assertOrder( X_EQ_Y, "1.sp", "1.Sp" );

        assertOrder( X_EQ_Y, "1.unknown", "1.UNKNOWN" );
        assertOrder( X_EQ_Y, "1.unknown", "1.Unknown" );
    }

    @Test
    public void testCaseInsensitiveOrderingOfQualifiersIsLocaleIndependent()
    {
        Locale orig = Locale.getDefault();
        try
        {
            Locale[] locales = { Locale.ENGLISH, new Locale( "tr" ) };
            for ( Locale locale : locales )
            {
                Locale.setDefault( locale );
                assertOrder( X_EQ_Y, "1-abcdefghijklmnopqrstuvwxyz", "1-ABCDEFGHIJKLMNOPQRSTUVWXYZ" );
            }
        }
        finally
        {
            Locale.setDefault( orig );
        }
    }

    @Test
    public void testQualifierVersusNumberOrdering()
    {
        assertOrder( X_LT_Y, "1-ga", "1-1" );
        assertOrder( X_LT_Y, "1.ga", "1.1" );
        assertOrder( X_EQ_Y, "1-ga", "1.0" );
        assertOrder( X_EQ_Y, "1.ga", "1.0" );

        assertOrder( X_LT_Y, "1-ga-1", "1-0-1" );
        assertOrder( X_LT_Y, "1.ga.1", "1.0.1" );

        assertOrder( X_GT_Y, "1.sp", "1.0" );
        assertOrder( X_LT_Y, "1.sp", "1.1" );

        assertOrder( X_LT_Y, "1-abc", "1-1" );
        assertOrder( X_LT_Y, "1.abc", "1.1" );

        assertOrder( X_LT_Y, "1-xyz", "1-1" );
        assertOrder( X_LT_Y, "1.xyz", "1.1" );
    }

    @Test
    public void testVersionEvolution()
    {
        assertSequence( "0.9.9-SNAPSHOT", "0.9.9", "0.9.10-SNAPSHOT", "0.9.10", "1.0-alpha-2-SNAPSHOT", "1.0-alpha-2",
                        "1.0-alpha-10-SNAPSHOT", "1.0-alpha-10", "1.0-beta-1-SNAPSHOT", "1.0-beta-1",
                        "1.0-rc-1-SNAPSHOT", "1.0-rc-1", "1.0-SNAPSHOT", "1.0", "1.0-sp-1-SNAPSHOT", "1.0-sp-1",
                        "1.0.1-alpha-1-SNAPSHOT", "1.0.1-alpha-1", "1.0.1-beta-1-SNAPSHOT", "1.0.1-beta-1",
                        "1.0.1-rc-1-SNAPSHOT", "1.0.1-rc-1", "1.0.1-SNAPSHOT", "1.0.1", "1.1-SNAPSHOT", "1.1" );

        assertSequence( "1.0-alpha", "1.0", "1.0-1" );
        assertSequence( "1.0.alpha", "1.0", "1.0-1" );
        assertSequence( "1.0-alpha", "1.0", "1.0.1" );
        assertSequence( "1.0.alpha", "1.0", "1.0.1" );
    }

    @Test
    public void testMinimumSegment()
    {
        assertOrder( X_LT_Y, "1.min", "1.0-alpha-1" );
        assertOrder( X_LT_Y, "1.min", "1.0-SNAPSHOT" );
        assertOrder( X_LT_Y, "1.min", "1.0" );
        assertOrder( X_LT_Y, "1.min", "1.9999999999" );

        assertOrder( X_EQ_Y, "1.min", "1.MIN" );

        assertOrder( X_GT_Y, "1.min", "0.99999" );
        assertOrder( X_GT_Y, "1.min", "0.max" );
    }

    @Test
    public void testMaximumSegment()
    {
        assertOrder( X_GT_Y, "1.max", "1.0-alpha-1" );
        assertOrder( X_GT_Y, "1.max", "1.0-SNAPSHOT" );
        assertOrder( X_GT_Y, "1.max", "1.0" );
        assertOrder( X_GT_Y, "1.max", "1.9999999999" );

        assertOrder( X_EQ_Y, "1.max", "1.MAX" );

        assertOrder( X_LT_Y, "1.max", "2.0-alpha-1" );
        assertOrder( X_LT_Y, "1.max", "2.min" );
    }

}
