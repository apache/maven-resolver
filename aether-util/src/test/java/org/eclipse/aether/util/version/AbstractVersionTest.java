/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.version;

import static org.junit.Assert.assertEquals;

import org.eclipse.aether.version.Version;

/**
 */
abstract class AbstractVersionTest
{

    protected static final int X_LT_Y = -1;

    protected static final int X_EQ_Y = 0;

    protected static final int X_GT_Y = 1;

    protected abstract Version newVersion( String version );

    protected void assertOrder( int expected, String version1, String version2 )
    {
        Version v1 = newVersion( version1 );
        Version v2 = newVersion( version2 );

        if ( expected > 0 )
        {
            assertEquals( "expected " + v1 + " > " + v2, 1, Integer.signum( v1.compareTo( v2 ) ) );
            assertEquals( "expected " + v2 + " < " + v1, -1, Integer.signum( v2.compareTo( v1 ) ) );
            assertEquals( "expected " + v1 + " != " + v2, false, v1.equals( v2 ) );
            assertEquals( "expected " + v2 + " != " + v1, false, v2.equals( v1 ) );
        }
        else if ( expected < 0 )
        {
            assertEquals( "expected " + v1 + " < " + v2, -1, Integer.signum( v1.compareTo( v2 ) ) );
            assertEquals( "expected " + v2 + " > " + v1, 1, Integer.signum( v2.compareTo( v1 ) ) );
            assertEquals( "expected " + v1 + " != " + v2, false, v1.equals( v2 ) );
            assertEquals( "expected " + v2 + " != " + v1, false, v2.equals( v1 ) );
        }
        else
        {
            assertEquals( "expected " + v1 + " == " + v2, 0, v1.compareTo( v2 ) );
            assertEquals( "expected " + v2 + " == " + v1, 0, v2.compareTo( v1 ) );
            assertEquals( "expected " + v1 + " == " + v2, true, v1.equals( v2 ) );
            assertEquals( "expected " + v2 + " == " + v1, true, v2.equals( v1 ) );
            assertEquals( "expected #(" + v1 + ") == #(" + v1 + ")", v1.hashCode(), v2.hashCode() );
        }
    }

    protected void assertSequence( String... versions )
    {
        for ( int i = 0; i < versions.length - 1; i++ )
        {
            for ( int j = i + 1; j < versions.length; j++ )
            {
                assertOrder( X_LT_Y, versions[i], versions[j] );
            }
        }
    }

}
