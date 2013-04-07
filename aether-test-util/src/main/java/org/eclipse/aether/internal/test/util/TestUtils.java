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
package org.eclipse.aether.internal.test.util;

import java.util.List;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.graph.Dependency;

/**
 * Utility methods to help unit testing.
 */
public class TestUtils
{

    private TestUtils()
    {
        // hide constructor
    }

    /**
     * Creates a new repository session whose local repository manager is initialized with an instance of
     * {@link TestLocalRepositoryManager}.
     */
    public static DefaultRepositorySystemSession newSession()
    {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        session.setLocalRepositoryManager( new TestLocalRepositoryManager() );
        return session;
    }

    /**
     * Creates a new dependency collection context.
     */
    public static DependencyCollectionContext newCollectionContext( RepositorySystemSession session,
                                                                    Dependency dependency,
                                                                    List<Dependency> managedDependencies )
    {
        return new TestDependencyCollectionContext( session, null, dependency, managedDependencies );
    }

    /**
     * Creates a new dependency collection context.
     */
    public static DependencyCollectionContext newCollectionContext( RepositorySystemSession session, Artifact artifact,
                                                                    Dependency dependency,
                                                                    List<Dependency> managedDependencies )
    {
        return new TestDependencyCollectionContext( session, artifact, dependency, managedDependencies );
    }

    /**
     * Creates a new dependency graph transformation context.
     */
    public static DependencyGraphTransformationContext newTransformationContext( RepositorySystemSession session )
    {
        return new TestDependencyGraphTransformationContext( session );
    }

}
