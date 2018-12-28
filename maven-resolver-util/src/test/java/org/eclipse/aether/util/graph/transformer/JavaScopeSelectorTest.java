package org.eclipse.aether.util.graph.transformer;

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

import java.util.Locale;

import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.Test;

public class JavaScopeSelectorTest
    extends AbstractDependencyGraphTransformerTest
{

    private enum Scope
    {
        TEST, PROVIDED, RUNTIME, COMPILE;

        @Override
        public String toString()
        {
            return super.name().toLowerCase( Locale.ENGLISH );
        }
    }

    @Override
    protected DependencyGraphTransformer newTransformer()
    {
        return new ConflictResolver( new NearestVersionSelector(), new JavaScopeSelector(),
                                     new SimpleOptionalitySelector(), new JavaScopeDeriver() );
    }

    @Override
    protected DependencyGraphParser newParser()
    {
        return new DependencyGraphParser( "transformer/scope-calculator/" );
    }

    private void expectScope( String expected, DependencyNode root, int... coords )
    {
        expectScope( null, expected, root, coords );
    }

    private void expectScope( String msg, String expected, DependencyNode root, int... coords )
    {
        if ( msg == null )
        {
            msg = "";
        }
        try
        {
            DependencyNode node = root;
            node = path( node, coords );

            assertEquals( msg + "\nculprit: " + node.toString() + "\n", expected, node.getDependency().getScope() );
        }
        catch ( IndexOutOfBoundsException | NullPointerException e )
        {
            throw new IllegalArgumentException( "illegal coordinates for child", e );
        }
    }

    private DependencyNode path( DependencyNode node, int... coords )
    {
        for ( int coord : coords )
        {
            node = node.getChildren().get( coord );
        }
        return node;
    }

    @Test
    public void testScopeInheritanceProvided()
        throws Exception
    {
        String resource = "inheritance.txt";

        String expected = "test";
        DependencyNode root = transform( parseResource( resource, "provided", "test" ) );
        expectScope( parser.dump( root ), expected, root, 0, 0 );
    }

    @Test
    public void testConflictWinningScopeGetsUsedForInheritance()
        throws Exception
    {
        DependencyNode root = parseResource( "conflict-and-inheritance.txt" );
        assertSame( root, transform( root ) );

        expectScope( "compile", root, 0, 0 );
        expectScope( "compile", root, 0, 0, 0 );
    }

    @Test
    public void testScopeOfDirectDependencyWinsConflictAndGetsUsedForInheritanceToChildrenEverywhereInGraph()
        throws Exception
    {
        DependencyNode root = parseResource( "direct-with-conflict-and-inheritance.txt" );
        assertSame( root, transform( root ) );

        expectScope( "test", root, 0, 0 );
    }

    @Test
    public void testCycleA()
        throws Exception
    {
        DependencyNode root = parseResource( "cycle-a.txt" );
        assertSame( root, transform( root ) );

        expectScope( "compile", root, 0 );
        expectScope( "runtime", root, 1 );
    }

    @Test
    public void testCycleB()
        throws Exception
    {
        DependencyNode root = parseResource( "cycle-b.txt" );
        assertSame( root, transform( root ) );

        expectScope( "runtime", root, 0 );
        expectScope( "compile", root, 1 );
    }

    @Test
    public void testCycleC()
        throws Exception
    {
        DependencyNode root = parseResource( "cycle-c.txt" );
        assertSame( root, transform( root ) );

        expectScope( "runtime", root, 0 );
        expectScope( "runtime", root, 0, 0 );
        expectScope( "runtime", root, 1 );
        expectScope( "runtime", root, 1, 0 );
    }

    @Test
    public void testCycleD()
        throws Exception
    {
        DependencyNode root = parseResource( "cycle-d.txt" );
        assertSame( root, transform( root ) );

        expectScope( "compile", root, 0 );
        expectScope( "compile", root, 0, 0 );
    }

    @Test
    public void testDirectNodesAlwaysWin()
        throws Exception
    {

        for ( Scope directScope : Scope.values() )
        {
            String direct = directScope.toString();

            DependencyNode root = parseResource( "direct-nodes-winning.txt", direct );

            String msg =
                String.format( "direct node should be setting scope ('%s') for all nodes.\n" + parser.dump( root ),
                               direct );
            assertSame( root, transform( root ) );
            msg += "\ntransformed:\n" + parser.dump( root );

            expectScope( msg, direct, root, 0 );
        }
    }

    @Test
    public void testNonDirectMultipleInheritance()
        throws Exception
    {
        for ( Scope scope1 : Scope.values() )
        {
            for ( Scope scope2 : Scope.values() )
            {
                DependencyNode root = parseResource( "multiple-inheritance.txt", scope1.toString(), scope2.toString() );

                String expected = scope1.compareTo( scope2 ) >= 0 ? scope1.toString() : scope2.toString();
                String msg = String.format( "expected '%s' to win\n" + parser.dump( root ), expected );

                assertSame( root, transform( root ) );
                msg += "\ntransformed:\n" + parser.dump( root );

                expectScope( msg, expected, root, 0, 0 );
            }
        }
    }

    @Test
    public void testConflictScopeOrdering()
        throws Exception
    {
        for ( Scope scope1 : Scope.values() )
        {
            for ( Scope scope2 : Scope.values() )
            {
                DependencyNode root = parseResource( "dueling-scopes.txt", scope1.toString(), scope2.toString() );

                String expected = scope1.compareTo( scope2 ) >= 0 ? scope1.toString() : scope2.toString();
                String msg = String.format( "expected '%s' to win\n" + parser.dump( root ), expected );

                assertSame( root, transform( root ) );
                msg += "\ntransformed:\n" + parser.dump( root );

                expectScope( msg, expected, root, 0, 0 );
            }
        }
    }

    /**
     * obscure case (illegal maven POM).
     */
    @Test
    public void testConflictingDirectNodes()
        throws Exception
    {
        for ( Scope scope1 : Scope.values() )
        {
            for ( Scope scope2 : Scope.values() )
            {
                DependencyNode root = parseResource( "conflicting-direct-nodes.txt", scope1.toString(), scope2.toString() );

                String expected = scope1.toString();
                String msg = String.format( "expected '%s' to win\n" + parser.dump( root ), expected );

                assertSame( root, transform( root ) );
                msg += "\ntransformed:\n" + parser.dump( root );

                expectScope( msg, expected, root, 0 );
            }
        }
    }

}
