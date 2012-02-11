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
package org.eclipse.aether.util.graph.traverser;

import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.Dependency;

/**
 * A dependency traverser that excludes the dependencies of fat artifacts from the traversal. Fat artifacts are
 * artifacts that have the property {@link org.eclipse.aether.artifact.ArtifactProperties#INCLUDES_DEPENDENCIES} set to
 * {@code true}.
 * 
 * @see org.eclipse.aether.artifact.Artifact#getProperties()
 */
public final class FatArtifactTraverser
    implements DependencyTraverser
{

    /**
     * Creates a new instance of this dependency traverser.
     */
    public FatArtifactTraverser()
    {
    }

    public boolean traverseDependency( Dependency dependency )
    {
        String prop = dependency.getArtifact().getProperty( ArtifactProperties.INCLUDES_DEPENDENCIES, "" );
        return !Boolean.parseBoolean( prop );
    }

    public DependencyTraverser deriveChildTraverser( DependencyCollectionContext context )
    {
        return this;
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
