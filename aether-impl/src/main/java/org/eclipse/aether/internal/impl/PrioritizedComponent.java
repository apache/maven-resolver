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

final class PrioritizedComponent<T>
    implements Comparable<PrioritizedComponent<?>>
{

    private final T component;

    private final Class<?> type;

    private final float priority;

    private final int index;

    public PrioritizedComponent( T component, Class<?> type, float priority, int index )
    {
        this.component = component;
        this.type = type;
        this.priority = priority;
        this.index = index;
    }

    public T getComponent()
    {
        return component;
    }

    public Class<?> getType()
    {
        return type;
    }

    public float getPriority()
    {
        return priority;
    }

    public boolean isDisabled()
    {
        return Float.isNaN( priority );
    }

    public int compareTo( PrioritizedComponent<?> o )
    {
        int rel = ( isDisabled() ? 1 : 0 ) - ( o.isDisabled() ? 1 : 0 );
        if ( rel == 0 )
        {
            rel = Float.compare( o.priority, priority );
            if ( rel == 0 )
            {
                rel = index - o.index;
            }
        }
        return rel;
    }

    @Override
    public String toString()
    {
        return priority + " (#" + index + "): " + String.valueOf( component );
    }

}
