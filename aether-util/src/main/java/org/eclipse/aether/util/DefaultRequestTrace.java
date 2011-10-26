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

import org.eclipse.aether.RequestTrace;

/**
 * A simple request trace.
 */
public final class DefaultRequestTrace
    implements RequestTrace
{

    private final RequestTrace parent;

    private final Object data;

    /**
     * Creates a child of the specified request trace. This method is basically a convenience that will invoke
     * {@link RequestTrace#newChild(Object)} when the specified parent trace is not {@code null} or otherwise
     * instantiante a new root trace.
     * 
     * @param parent The parent request trace, may be {@code null}.
     * @param data The data to associate with the child trace, may be {@code null}.
     * @return The child trace, never {@code null}.
     */
    public static RequestTrace newChild( RequestTrace parent, Object data )
    {
        if ( parent == null )
        {
            return new DefaultRequestTrace( data );
        }
        return parent.newChild( data );
    }

    /**
     * Creates a new root trace with the specified data.
     * 
     * @param data The data to associate with the trace, may be {@code null}.
     */
    public DefaultRequestTrace( Object data )
    {
        this( null, data );
    }

    private DefaultRequestTrace( RequestTrace parent, Object data )
    {
        this.parent = parent;
        this.data = data;
    }

    public Object getData()
    {
        return data;
    }

    public RequestTrace getParent()
    {
        return parent;
    }

    public RequestTrace newChild( Object data )
    {
        return new DefaultRequestTrace( this, data );
    }

    @Override
    public String toString()
    {
        return String.valueOf( getData() );
    }

}
