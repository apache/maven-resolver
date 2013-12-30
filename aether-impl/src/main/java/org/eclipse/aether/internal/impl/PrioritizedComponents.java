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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Helps to sort pluggable components by their priority.
 */
final class PrioritizedComponents<T>
{

    private final static String FACTORY_SUFFIX = "Factory";

    private final Map<?, ?> configProps;

    private final List<PrioritizedComponent<T>> components;

    private int firstDisabled;

    public PrioritizedComponents( RepositorySystemSession session )
    {
        this( session.getConfigProperties() );
    }

    PrioritizedComponents( Map<?, ?> configurationProperties )
    {
        configProps = configurationProperties;
        components = new ArrayList<PrioritizedComponent<T>>();
        firstDisabled = 0;
    }

    public void add( T component, float priority )
    {
        Class<?> type = getImplClass( component );
        priority = ConfigUtils.getFloat( configProps, priority, getConfigKeys( type ) );
        PrioritizedComponent<T> pc = new PrioritizedComponent<T>( component, type, priority );

        int index = Collections.binarySearch( components, pc );
        if ( index < 0 )
        {
            index = -index - 1;
        }
        else
        {
            index++;
        }
        components.add( index, pc );

        if ( index <= firstDisabled && !pc.isDisabled() )
        {
            firstDisabled++;
        }
    }

    private static Class<?> getImplClass( Object component )
    {
        Class<?> type = component.getClass();
        // detect and ignore CGLIB-based proxy classes employed by Guice for AOP (cf. BytecodeGen.newEnhancer)
        int idx = type.getName().indexOf( "$$" );
        if ( idx >= 0 )
        {
            Class<?> base = type.getSuperclass();
            if ( base != null && idx == base.getName().length() && type.getName().startsWith( base.getName() ) )
            {
                type = base;
            }
        }
        return type;
    }

    static String[] getConfigKeys( Class<?> type )
    {
        List<String> keys = new ArrayList<String>();
        keys.add( ConfigurationProperties.PREFIX_PRIORITY + type.getName() );
        String sn = type.getSimpleName();
        keys.add( ConfigurationProperties.PREFIX_PRIORITY + sn );
        if ( sn.endsWith( FACTORY_SUFFIX ) )
        {
            keys.add( ConfigurationProperties.PREFIX_PRIORITY + sn.substring( 0, sn.length() - FACTORY_SUFFIX.length() ) );
        }
        return keys.toArray( new String[keys.size()] );
    }

    public boolean isEmpty()
    {
        return components.isEmpty();
    }

    public List<PrioritizedComponent<T>> getAll()
    {
        return components;
    }

    public List<PrioritizedComponent<T>> getEnabled()
    {
        return components.subList( 0, firstDisabled );
    }

    public void list( StringBuilder buffer )
    {
        for ( int i = 0; i < components.size(); i++ )
        {
            if ( i > 0 )
            {
                buffer.append( ", " );
            }
            PrioritizedComponent<?> component = components.get( i );
            buffer.append( component.getType().getSimpleName() );
            if ( component.isDisabled() )
            {
                buffer.append( " (disabled)" );
            }
        }
    }

    @Override
    public String toString()
    {
        return components.toString();
    }

}
