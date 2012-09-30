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
package org.eclipse.aether.internal.impl;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.internal.impl.DefaultDependencyCollector;
import org.eclipse.aether.internal.test.impl.TestRepositorySystemSession;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.util.graph.manager.ClassicDependencyManager;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class DefaultDependencyCollectorTest
{

    private DefaultDependencyCollector collector;

    private TestRepositorySystemSession session;

    private DependencyGraphParser parser;

    private RemoteRepository repository;

    @Before
    public void setup()
        throws IOException
    {
        session = new TestRepositorySystemSession();

        collector = new DefaultDependencyCollector();
        collector.setArtifactDescriptorReader( new IniArtifactDescriptorReader( "artifact-descriptions/" ) );
        collector.setVersionRangeResolver( new StubVersionRangeResolver() );
        collector.setRemoteRepositoryManager( new StubRemoteRepositoryManager() );

        parser = new DependencyGraphParser( "artifact-descriptions/" );

        repository = new RemoteRepository.Builder( "id", "default", "file:///" ).build();
    }

    private static void assertEqualSubtree( DependencyNode expected, DependencyNode actual )
    {
        assertEqualSubtree( expected, actual, new LinkedList<DependencyNode>() );
    }

    private static void assertEqualSubtree( DependencyNode expected, DependencyNode actual,
                                            LinkedList<DependencyNode> parents )
    {
        assertEquals( "path: " + parents, expected.getDependency(), actual.getDependency() );

        if ( actual.getDependency() != null )
        {
            Artifact artifact = actual.getDependency().getArtifact();
            for ( DependencyNode parent : parents )
            {
                if ( parent.getDependency() != null && artifact.equals( parent.getDependency().getArtifact() ) )
                {
                    return;
                }
            }
        }

        parents.addLast( expected );

        assertEquals( "path: " + parents + ", expected: " + expected.getChildren() + ", actual: "
                          + actual.getChildren(), expected.getChildren().size(), actual.getChildren().size() );

        Iterator<DependencyNode> iterator1 = expected.getChildren().iterator();
        Iterator<DependencyNode> iterator2 = actual.getChildren().iterator();

        while ( iterator1.hasNext() )
        {
            assertEqualSubtree( iterator1.next(), iterator2.next(), parents );
        }

        parents.removeLast();
    }

    private Dependency dep( DependencyNode root, int... coords )
    {
        return path( root, coords ).getDependency();
    }

    private DependencyNode path( DependencyNode root, int... coords )
    {
        try
        {
            DependencyNode node = root;
            for ( int i = 0; i < coords.length; i++ )
            {
                node = node.getChildren().get( coords[i] );
            }

            return node;
        }
        catch ( IndexOutOfBoundsException e )
        {
            throw new IllegalArgumentException( "Illegal coordinates for child", e );
        }
        catch ( NullPointerException e )
        {
            throw new IllegalArgumentException( "Illegal coordinates for child", e );
        }
    }

    @Test
    public void testSimpleCollection()
        throws IOException, DependencyCollectionException
    {
        DependencyNode root = parser.parseLiteral( "gid:aid:ext:ver" );
        Dependency dependency = root.getDependency();
        CollectRequest request = new CollectRequest( dependency, Arrays.asList( repository ) );
        CollectResult result = collector.collectDependencies( session, request );

        assertEquals( 0, result.getExceptions().size() );

        DependencyNode newRoot = result.getRoot();
        Dependency newDependency = newRoot.getDependency();

        assertEquals( dependency, newDependency );
        assertEquals( dependency.getArtifact(), newDependency.getArtifact() );

        assertEquals( 1, newRoot.getChildren().size() );

        DependencyNode expect = parser.parseLiteral( "gid:aid2:ext:ver:compile" );
        assertEquals( expect.getDependency(), newRoot.getChildren().get( 0 ).getDependency() );
    }

    @Test
    public void testMissingDependencyDescription()
        throws IOException
    {
        DependencyNode root = parser.parseLiteral( "missing:description:ext:ver" );
        CollectRequest request = new CollectRequest( root.getDependency(), Arrays.asList( repository ) );
        try
        {
            collector.collectDependencies( session, request );
            fail( "expected exception" );
        }
        catch ( DependencyCollectionException e )
        {
            CollectResult result = e.getResult();
            assertSame( request, result.getRequest() );
            assertNotNull( result.getExceptions() );
            assertEquals( 1, result.getExceptions().size() );

            assertTrue( result.getExceptions().get( 0 ) instanceof ArtifactDescriptorException );

            assertEquals( request.getRoot(), result.getRoot().getDependency() );
        }
    }

    @Test
    public void testDuplicates()
        throws IOException, DependencyCollectionException
    {
        DependencyNode root = parser.parseLiteral( "duplicate:transitive:ext:dependency" );
        Dependency dependency = root.getDependency();
        CollectRequest request = new CollectRequest( dependency, Arrays.asList( repository ) );

        CollectResult result = collector.collectDependencies( session, request );

        assertEquals( 0, result.getExceptions().size() );

        DependencyNode newRoot = result.getRoot();
        Dependency newDependency = newRoot.getDependency();

        assertEquals( dependency, newDependency );
        assertEquals( dependency.getArtifact(), newDependency.getArtifact() );

        assertEquals( 2, newRoot.getChildren().size() );

        DependencyNode expect = parser.parseLiteral( "gid:aid:ext:ver:compile" );
        Dependency dep = expect.getDependency();
        assertEquals( dep, dep( newRoot, 0 ) );

        expect = parser.parseLiteral( "gid:aid2:ext:ver:compile" );
        dep = expect.getDependency();
        assertEquals( dep, dep( newRoot, 1 ) );
        assertEquals( dep, dep( newRoot, 0, 0 ) );
        assertEquals( dep( newRoot, 1 ), dep( newRoot, 0, 0 ) );
    }

    @Test
    public void testEqualSubtree()
        throws IOException, DependencyCollectionException
    {
        DependencyNode root = parser.parse( "expectedSubtreeComparisonResult.txt" );
        Dependency dependency = root.getDependency();
        CollectRequest request = new CollectRequest( dependency, Arrays.asList( repository ) );

        CollectResult result = collector.collectDependencies( session, request );
        assertEqualSubtree( root, result.getRoot() );
    }

    @Test
    public void testCyclicDependencies()
        throws Exception
    {
        DependencyNode root = parser.parse( "cycle.txt" );
        CollectRequest request = new CollectRequest( root.getDependency(), Arrays.asList( repository ) );
        CollectResult result = collector.collectDependencies( session, request );
        assertEqualSubtree( root, result.getRoot() );
    }

    @Test
    public void testCyclicDependenciesBig()
        throws Exception
    {
        DependencyNode root = parser.parse( "cycle-big.txt" );
        CollectRequest request = new CollectRequest( root.getDependency(), Arrays.asList( repository ) );
        collector.setArtifactDescriptorReader( new IniArtifactDescriptorReader( "artifact-descriptions/cycle-big/" ) );
        CollectResult result = collector.collectDependencies( session, request );
        assertNotNull( result.getRoot() );
        // we only care about the performance here, this test must not hang or run out of mem
    }

    @Test
    public void testPartialResultOnError()
        throws IOException
    {
        DependencyNode root = parser.parse( "expectedPartialSubtreeOnError.txt" );

        Dependency dependency = root.getDependency();
        CollectRequest request = new CollectRequest( dependency, Arrays.asList( repository ) );

        CollectResult result;
        try
        {
            result = collector.collectDependencies( session, request );
            fail( "expected exception " );
        }
        catch ( DependencyCollectionException e )
        {
            result = e.getResult();

            assertSame( request, result.getRequest() );
            assertNotNull( result.getExceptions() );
            assertEquals( 1, result.getExceptions().size() );

            assertTrue( result.getExceptions().get( 0 ) instanceof ArtifactDescriptorException );

            assertEqualSubtree( root, result.getRoot() );
        }
    }

    @Test
    public void testCollectMultipleDependencies()
        throws IOException, DependencyCollectionException
    {
        DependencyNode root1 = parser.parseLiteral( "gid:aid:ext:ver:compile" );
        DependencyNode root2 = parser.parseLiteral( "gid:aid2:ext:ver:compile" );
        List<Dependency> dependencies = Arrays.asList( root1.getDependency(), root2.getDependency() );
        CollectRequest request = new CollectRequest( dependencies, null, Arrays.asList( repository ) );
        CollectResult result = collector.collectDependencies( session, request );

        assertEquals( 0, result.getExceptions().size() );
        assertEquals( 2, result.getRoot().getChildren().size() );
        assertEquals( root1.getDependency(), dep( result.getRoot(), 0 ) );

        assertEquals( 1, path( result.getRoot(), 0 ).getChildren().size() );
        assertEquals( root2.getDependency(), dep( result.getRoot(), 0, 0 ) );

        assertEquals( 0, path( result.getRoot(), 1 ).getChildren().size() );
        assertEquals( root2.getDependency(), dep( result.getRoot(), 1 ) );
    }

    @Test
    public void testArtifactDescriptorResolutionNotRestrictedToRepoHostingSelectedVersion()
        throws Exception
    {
        RemoteRepository repo2 = new RemoteRepository.Builder( "test", "default", "file:///" ).build();

        final List<RemoteRepository> repos = new ArrayList<RemoteRepository>();

        collector.setArtifactDescriptorReader( new ArtifactDescriptorReader()
        {
            public ArtifactDescriptorResult readArtifactDescriptor( RepositorySystemSession session,
                                                                    ArtifactDescriptorRequest request )
                throws ArtifactDescriptorException
            {
                repos.addAll( request.getRepositories() );
                return new ArtifactDescriptorResult( request );
            }
        } );

        DependencyNode root = parser.parseLiteral( "verrange:parent:jar:[1,):compile" );
        List<Dependency> dependencies = Arrays.asList( root.getDependency() );
        CollectRequest request = new CollectRequest( dependencies, null, Arrays.asList( repository, repo2 ) );
        CollectResult result = collector.collectDependencies( session, request );

        assertEquals( 0, result.getExceptions().size() );
        assertEquals( 2, repos.size() );
        assertEquals( "id", repos.get( 0 ).getId() );
        assertEquals( "test", repos.get( 1 ).getId() );
    }

    @Test
    public void testManagedVersionScope()
        throws IOException, DependencyCollectionException
    {
        DependencyNode root = parser.parseLiteral( "managed:aid:ext:ver" );
        Dependency dependency = root.getDependency();
        CollectRequest request = new CollectRequest( dependency, Arrays.asList( repository ) );

        session.setDependencyManager( new ClassicDependencyManager() );

        CollectResult result = collector.collectDependencies( session, request );

        assertEquals( 0, result.getExceptions().size() );

        DependencyNode newRoot = result.getRoot();

        assertEquals( dependency, dep( newRoot ) );
        assertEquals( dependency.getArtifact(), dep( newRoot ).getArtifact() );

        assertEquals( 1, newRoot.getChildren().size() );
        DependencyNode expect = parser.parseLiteral( "gid:aid:ext:ver:compile" );
        assertEquals( dep( expect ), dep( newRoot, 0 ) );

        assertEquals( 1, path( newRoot, 0 ).getChildren().size() );
        expect = parser.parseLiteral( "gid:aid2:ext:managedVersion:managedScope" );
        assertEquals( dep( expect ), dep( newRoot, 0, 0 ) );
    }

    @Test
    public void testDependencyManagement()
        throws IOException, DependencyCollectionException
    {
        collector.setArtifactDescriptorReader( new IniArtifactDescriptorReader( "artifact-descriptions/managed/" ) );

        DependencyNode root = parser.parse( "expectedSubtreeComparisonResult.txt" );
        TestDependencyManager depMgmt = new TestDependencyManager();
        depMgmt.addManagedDependency( dep( root, 0 ), "managed", null, null );
        depMgmt.addManagedDependency( dep( root, 0, 1 ), "managed", "managed", null );
        depMgmt.addManagedDependency( dep( root, 1 ), null, null, "managed" );
        session.setDependencyManager( depMgmt );

        // collect result will differ from expectedSubtreeComparisonResult.txt
        // set localPath -> no dependency traversal
        CollectRequest request = new CollectRequest( dep( root ), Arrays.asList( repository ) );
        CollectResult result = collector.collectDependencies( session, request );

        DependencyNode node = result.getRoot();
        assertEquals( "managed", dep( node, 0, 1 ).getArtifact().getVersion() );
        assertEquals( "managed", dep( node, 0, 1 ).getScope() );

        assertEquals( "managed", dep( node, 1 ).getArtifact().getProperty( ArtifactProperties.LOCAL_PATH, null ) );
        assertEquals( "managed", dep( node, 0, 0 ).getArtifact().getProperty( ArtifactProperties.LOCAL_PATH, null ) );
    }

    /*     */
    public class TestDependencyManager
        implements DependencyManager
    {
        private Map<Dependency, String> versions = new HashMap<Dependency, String>();

        private Map<Dependency, String> scopes = new HashMap<Dependency, String>();

        private Map<Dependency, String> paths = new HashMap<Dependency, String>();

        public void addManagedDependency( Dependency d, String version, String scope, String localPath )
        {
            versions.put( d, version );
            scopes.put( d, scope );
            paths.put( d, localPath );
        }

        public DependencyManagement manageDependency( Dependency d )
        {
            DependencyManagement mgmt = new DependencyManagement();
            mgmt.setVersion( versions.get( d ) );
            mgmt.setScope( scopes.get( d ) );
            String path = paths.get( d );
            if ( path != null )
            {
                Map<String, String> p = new HashMap<String, String>();
                p.put( ArtifactProperties.LOCAL_PATH, path );
                mgmt.setProperties( p );
            }
            return mgmt;
        }

        public DependencyManager deriveChildManager( DependencyCollectionContext context )
        {
            return this;
        }

    }

}
