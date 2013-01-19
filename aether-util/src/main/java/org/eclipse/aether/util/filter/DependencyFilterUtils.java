/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * A utility class assisting in the creation of dependency node filters.
 */
public final class DependencyFilterUtils
{

    private DependencyFilterUtils()
    {
        // hide constructor
    }

    /**
     * Creates a new filter that negates the specified filter.
     * 
     * @param filter The filter to negate, must not be {@code null}.
     * @return The new filter, never {@code null}.
     */
    public static DependencyFilter notFilter( DependencyFilter filter )
    {
        return new NotDependencyFilter( filter );
    }

    /**
     * Creates a new filter that combines the specified filters using a logical {@code AND}. If no filters are
     * specified, the resulting filter accepts everything.
     * 
     * @param filters The filters to combine, may be {@code null}.
     * @return The new filter, never {@code null}.
     */
    public static DependencyFilter andFilter( DependencyFilter... filters )
    {
        if ( filters != null && filters.length == 1 )
        {
            return filters[0];
        }
        else
        {
            return new AndDependencyFilter( filters );
        }
    }

    /**
     * Creates a new filter that combines the specified filters using a logical {@code AND}. If no filters are
     * specified, the resulting filter accepts everything.
     * 
     * @param filters The filters to combine, may be {@code null}.
     * @return The new filter, never {@code null}.
     */
    public static DependencyFilter andFilter( Collection<DependencyFilter> filters )
    {
        if ( filters != null && filters.size() == 1 )
        {
            return filters.iterator().next();
        }
        else
        {
            return new AndDependencyFilter( filters );
        }
    }

    /**
     * Creates a new filter that combines the specified filters using a logical {@code OR}. If no filters are specified,
     * the resulting filter accepts nothing.
     * 
     * @param filters The filters to combine, may be {@code null}.
     * @return The new filter, never {@code null}.
     */
    public static DependencyFilter orFilter( DependencyFilter... filters )
    {
        if ( filters != null && filters.length == 1 )
        {
            return filters[0];
        }
        else
        {
            return new OrDependencyFilter( filters );
        }
    }

    /**
     * Creates a new filter that combines the specified filters using a logical {@code OR}. If no filters are specified,
     * the resulting filter accepts nothing.
     * 
     * @param filters The filters to combine, may be {@code null}.
     * @return The new filter, never {@code null}.
     */
    public static DependencyFilter orFilter( Collection<DependencyFilter> filters )
    {
        if ( filters != null && filters.size() == 1 )
        {
            return filters.iterator().next();
        }
        else
        {
            return new OrDependencyFilter( filters );
        }
    }

    /**
     * Creates a new filter that selects dependencies whose scope matches one or more of the specified classpath types.
     * A classpath type is a set of scopes separated by either {@code ','} or {@code '+'}.
     * 
     * @param classpathTypes The classpath types, may be {@code null} or empty to match no dependency.
     * @return The new filter, never {@code null}.
     * @see JavaScopes
     */
    public static DependencyFilter classpathFilter( String... classpathTypes )
    {
        return classpathFilter( ( classpathTypes != null ) ? Arrays.asList( classpathTypes ) : null );
    }

    /**
     * Creates a new filter that selects dependencies whose scope matches one or more of the specified classpath types.
     * A classpath type is a set of scopes separated by either {@code ','} or {@code '+'}.
     * 
     * @param classpathTypes The classpath types, may be {@code null} or empty to match no dependency.
     * @return The new filter, never {@code null}.
     * @see JavaScopes
     */
    public static DependencyFilter classpathFilter( Collection<String> classpathTypes )
    {
        Collection<String> types = new HashSet<String>();

        if ( classpathTypes != null )
        {
            for ( String classpathType : classpathTypes )
            {
                String[] tokens = classpathType.split( "[+,]" );
                for ( String token : tokens )
                {
                    token = token.trim();
                    if ( token.length() > 0 )
                    {
                        types.add( token );
                    }
                }
            }
        }

        Collection<String> included = new HashSet<String>();
        for ( String type : types )
        {
            if ( JavaScopes.COMPILE.equals( type ) )
            {
                Collections.addAll( included, JavaScopes.COMPILE, JavaScopes.PROVIDED, JavaScopes.SYSTEM );
            }
            else if ( JavaScopes.RUNTIME.equals( type ) )
            {
                Collections.addAll( included, JavaScopes.COMPILE, JavaScopes.RUNTIME );
            }
            else if ( JavaScopes.TEST.equals( type ) )
            {
                Collections.addAll( included, JavaScopes.COMPILE, JavaScopes.PROVIDED, JavaScopes.SYSTEM,
                                    JavaScopes.RUNTIME, JavaScopes.TEST );
            }
            else
            {
                included.add( type );
            }
        }

        Collection<String> excluded = new HashSet<String>();
        Collections.addAll( excluded, JavaScopes.COMPILE, JavaScopes.PROVIDED, JavaScopes.SYSTEM, JavaScopes.RUNTIME,
                            JavaScopes.TEST );
        excluded.removeAll( included );

        return new ScopeDependencyFilter( null, excluded );
    }

}
