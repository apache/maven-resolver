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
package org.eclipse.aether.util;

import static org.junit.Assert.*;

import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.util.DefaultRequestTrace;
import org.junit.Test;

/**
 */
public class DefaultRequestTraceTest
{

    @Test
    public void testConstructor()
    {
        DefaultRequestTrace trace = new DefaultRequestTrace( null );
        assertSame( null, trace.getData() );

        trace = new DefaultRequestTrace( this );
        assertSame( this, trace.getData() );
    }

    @Test
    public void testParentChaining()
    {
        RequestTrace trace1 = new DefaultRequestTrace( null );
        RequestTrace trace2 = trace1.newChild( this );

        assertSame( null, trace1.getParent() );
        assertSame( null, trace1.getData() );
        assertSame( trace1, trace2.getParent() );
        assertSame( this, trace2.getData() );
    }

    @Test
    public void testNewChildRequestTrace()
    {
        RequestTrace trace = DefaultRequestTrace.newChild( null, this );
        assertNotNull( trace );
        assertSame( null, trace.getParent() );
        assertSame( this, trace.getData() );
    }

}
