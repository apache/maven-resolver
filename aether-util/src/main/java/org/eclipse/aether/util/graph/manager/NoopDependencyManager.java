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
package org.eclipse.aether.util.graph.manager;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Dependency;

/**
 * A dependency manager that does not do any dependency management.
 */
public final class NoopDependencyManager
    implements DependencyManager
{

    /**
     * A ready-made instance of this dependency manager which can safely be reused throughout an entire application
     * regardless of multi-threading.
     */
    public static final DependencyManager INSTANCE = new NoopDependencyManager();

    /**
     * Creates a new instance of this dependency manager. Usually, {@link #INSTANCE} should be used instead.
     */
    public NoopDependencyManager()
    {
    }

    public DependencyManager deriveChildManager( DependencyCollectionContext context )
    {
        return this;
    }

    public DependencyManagement manageDependency( Dependency dependency )
    {
        return null;
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        else if ( null == obj || !getClass().equals( obj.getClass() ) )
        {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }

}
