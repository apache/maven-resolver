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
package org.eclipse.aether;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 */
public class RequestTraceTest
{

    @Test
    public void testConstructor()
    {
        RequestTrace trace = new RequestTrace( null );
        assertSame( null, trace.getData() );

        trace = new RequestTrace( this );
        assertSame( this, trace.getData() );
    }

    @Test
    public void testParentChaining()
    {
        RequestTrace trace1 = new RequestTrace( null );
        RequestTrace trace2 = trace1.newChild( this );

        assertSame( null, trace1.getParent() );
        assertSame( null, trace1.getData() );
        assertSame( trace1, trace2.getParent() );
        assertSame( this, trace2.getData() );
    }

    @Test
    public void testNewChildRequestTrace()
    {
        RequestTrace trace = RequestTrace.newChild( null, this );
        assertNotNull( trace );
        assertSame( null, trace.getParent() );
        assertSame( this, trace.getData() );
    }

}
