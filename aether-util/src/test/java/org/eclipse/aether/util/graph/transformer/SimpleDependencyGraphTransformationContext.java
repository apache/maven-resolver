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
package org.eclipse.aether.util.graph.transformer;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;

/**
 */
class SimpleDependencyGraphTransformationContext
    implements DependencyGraphTransformationContext
{

    private RepositorySystemSession session;

    private Map<Object, Object> map = new HashMap<Object, Object>();

    public SimpleDependencyGraphTransformationContext()
    {
        this( null );
    }

    public SimpleDependencyGraphTransformationContext( RepositorySystemSession session )
    {
        this.session = session;
    }

    public RepositorySystemSession getSession()
    {
        return session;
    }

    public Object get( Object key )
    {
        return map.get( key );
    }

    public Object put( Object key, Object value )
    {
        return map.put( key, value );
    }

}
