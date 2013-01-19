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
package org.eclipse.aether.util.graph.manager;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.junit.Before;
import org.junit.Test;

public class ClassicDependencyManagerTest
{

    private final Artifact A = new DefaultArtifact( "test", "a", "", "" );

    private final Artifact A1 = new DefaultArtifact( "test", "a", "", "1" );

    private final Artifact B = new DefaultArtifact( "test", "b", "", "" );

    private final Artifact B1 = new DefaultArtifact( "test", "b", "", "1" );

    private RepositorySystemSession session;

    private DependencyCollectionContext newContext( Dependency... managedDependencies )
    {
        return TestUtils.newCollectionContext( session, null, Arrays.asList( managedDependencies ) );
    }

    @Before
    public void setUp()
    {
        session = TestUtils.newSession();
    }

    @Test
    public void testManageOptional()
    {
        DependencyManager manager = new ClassicDependencyManager();

        manager =
            manager.deriveChildManager( newContext( new Dependency( A, null, null ), new Dependency( B, null, true ) ) );
        DependencyManagement mngt;
        mngt = manager.manageDependency( new Dependency( A1, null ) );
        assertNull( mngt );
        mngt = manager.manageDependency( new Dependency( B1, null ) );
        assertNull( mngt );

        manager = manager.deriveChildManager( newContext() );
        mngt = manager.manageDependency( new Dependency( A1, null ) );
        assertNull( mngt );
        mngt = manager.manageDependency( new Dependency( B1, null ) );
        assertNotNull( mngt );
        assertEquals( Boolean.TRUE, mngt.getOptional() );
    }

}
