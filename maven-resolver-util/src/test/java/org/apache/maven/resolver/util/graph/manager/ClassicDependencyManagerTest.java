package org.apache.maven.resolver.util.graph.manager;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import static org.junit.Assert.*;

import java.util.Arrays;

import org.apache.maven.resolver.RepositorySystemSession;
import org.apache.maven.resolver.artifact.Artifact;
import org.apache.maven.resolver.artifact.DefaultArtifact;
import org.apache.maven.resolver.collection.DependencyCollectionContext;
import org.apache.maven.resolver.collection.DependencyManagement;
import org.apache.maven.resolver.collection.DependencyManager;
import org.apache.maven.resolver.graph.Dependency;
import org.apache.maven.resolver.internal.test.util.TestUtils;
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
