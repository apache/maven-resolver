/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.graph.transformer;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.test.util.DependencyGraphParser;
import org.eclipse.aether.util.graph.transformer.JavaEffectiveScopeCalculator;
import org.junit.Before;
import org.junit.Test;

public class JavaEffectiveScopeCalculatorTest
{

    private DependencyGraphParser parser;

    private SimpleDependencyGraphTransformationContext ctx;

    private enum Scope
    {
        TEST, PROVIDED, RUNTIME, COMPILE;

        @Override
        public String toString()
        {
            return super.name().toLowerCase( Locale.ENGLISH );
        }
    }

    @Before
    public void setup()
    {
        parser = new DependencyGraphParser( "transformer/scope-calculator/" );
        ctx = new SimpleDependencyGraphTransformationContext();
    }

    private DependencyNode parse( String name, String... substitutions )
        throws IOException
    {
        parser.setSubstitutions( Arrays.asList( substitutions ) );
        return parser.parse( name );
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
        catch ( IndexOutOfBoundsException e )
        {
            throw new IllegalArgumentException( "Illegal coordinates for child", e );
        }
        catch ( NullPointerException e )
        {
            throw new IllegalArgumentException( "Illegal coordinates for child", e );
        }
    }

    private DependencyNode path( DependencyNode node, int... coords )
    {
        for ( int i = 0; i < coords.length; i++ )
        {
            node = node.getChildren().get( coords[i] );
        }
        return node;
    }

    private DependencyNode transform( DependencyNode root )
        throws RepositoryException
    {
        root = new SimpleConflictMarker().transformGraph( root, ctx );
        root = new JavaEffectiveScopeCalculator().transformGraph( root, ctx );
        return root;
    }

    @Test
    public void testScopeInheritanceProvided()
        throws IOException, RepositoryException
    {
        String resource = "inheritance.txt";

        String expected = "provided";
        int[] coords = new int[] { 0, 0 };
        expectScope( expected, transform( parse( resource, "provided", "test" ) ), coords );
    }

    @Test
    public void testConflictWinningScopeGetsUsedForInheritance()
        throws Exception
    {
        DependencyNode root = parser.parse( "conflict-and-inheritance.txt" );
        root = transform( root );

        expectScope( "compile", root, 0, 0 );
        expectScope( "compile", root, 0, 0, 0 );
    }

    @Test
    public void testScopeOfDirectDependencyWinsConflictAndGetsUsedForInheritanceToChildrenEverywhereInGraph()
        throws Exception
    {
        DependencyNode root = parser.parse( "direct-with-conflict-and-inheritance.txt" );
        root = transform( root );

        expectScope( "test", root, 0, 0 );
        expectScope( "test", root, 1, 0, 0 );
    }

    @Test
    public void testCycleA()
        throws Exception
    {
        DependencyNode root = parser.parse( "cycle-a.txt" );
        root = transform( root );

        expectScope( "compile", root, 0 );
        expectScope( "runtime", root, 0, 0 );
        expectScope( "runtime", root, 1 );
        expectScope( "compile", root, 1, 0 );
    }

    @Test
    public void testCycleB()
        throws Exception
    {
        DependencyNode root = parser.parse( "cycle-b.txt" );
        root = transform( root );

        expectScope( "runtime", root, 0 );
        expectScope( "compile", root, 0, 0 );
        expectScope( "compile", root, 1 );
        expectScope( "runtime", root, 1, 0 );
    }

    @Test
    public void testCycleC()
        throws Exception
    {
        DependencyNode root = parser.parse( "cycle-c.txt" );
        root = transform( root );

        expectScope( "runtime", root, 0 );
        expectScope( "runtime", root, 0, 0 );
        expectScope( "runtime", root, 0, 0, 0 );
        expectScope( "runtime", root, 1 );
        expectScope( "runtime", root, 1, 0 );
        expectScope( "runtime", root, 1, 0, 0 );
    }

    @Test
    public void testCycleD()
        throws Exception
    {
        DependencyNode root = parser.parse( "cycle-d.txt" );
        root = transform( root );

        expectScope( "compile", root, 0 );
        expectScope( "compile", root, 0, 0 );
        expectScope( "compile", root, 0, 0, 0 );
    }

    @Test
    public void testDirectNodesAlwaysWin()
        throws IOException, RepositoryException
    {

        for ( Scope directScope : Scope.values() )
        {
            String direct = directScope.toString();

            parser.setSubstitutions( direct );
            DependencyNode root = parser.parse( "direct-nodes-winning.txt" );

            String msg =
                String.format( "direct node should be setting scope ('%s') for all nodes.\n" + parser.dump( root ),
                               direct );
            root = transform( root );
            msg += "\ntransformed:\n" + parser.dump( root );

            expectScope( msg, direct, root, 0 );
            expectScope( msg, direct, root, 1, 0 );
            expectScope( msg, direct, root, 2, 0 );
            expectScope( msg, direct, root, 3, 0 );
            expectScope( msg, direct, root, 4, 0 );
        }
    }

    @Test
    public void testNonDirectMultipleInheritance()
        throws RepositoryException, IOException
    {
        for ( Scope scope1 : Scope.values() )
        {
            for ( Scope scope2 : Scope.values() )
            {
                parser.setSubstitutions( scope1.toString(), scope2.toString() );
                DependencyNode root = parser.parse( "multiple-inheritance.txt" );

                String expected = scope1.compareTo( scope2 ) >= 0 ? scope1.toString() : scope2.toString();
                String msg = String.format( "expected '%s' to win\n" + parser.dump( root ), expected );

                root = transform( root );
                msg += "\ntransformed:\n" + parser.dump( root );

                expectScope( msg, expected, root, 0, 0 );
                expectScope( msg, expected, root, 1, 0 );
            }
        }
    }

    @Test
    public void testConflictScopeOrdering()
        throws RepositoryException, IOException
    {
        for ( Scope scope1 : Scope.values() )
        {
            for ( Scope scope2 : Scope.values() )
            {
                parser.setSubstitutions( scope1.toString(), scope2.toString() );
                DependencyNode root = parser.parse( "dueling-scopes.txt" );

                String expected = scope1.compareTo( scope2 ) >= 0 ? scope1.toString() : scope2.toString();
                String msg = String.format( "expected '%s' to win\n" + parser.dump( root ), expected );

                root = transform( root );
                msg += "\ntransformed:\n" + parser.dump( root );

                expectScope( msg, expected, root, 0, 0 );
                expectScope( msg, expected, root, 1, 0 );
            }
        }
    }

    /**
     * obscure case (illegal maven POM). Current behavior: last mentioned wins.
     */
    @Test
    public void testConflictingDirectNodes()
        throws RepositoryException, IOException
    {
        for ( Scope scope1 : Scope.values() )
        {
            for ( Scope scope2 : Scope.values() )
            {
                parser.setSubstitutions( scope1.toString(), scope2.toString() );
                DependencyNode root = parser.parse( "conflicting-direct-nodes.txt" );

                String expected = scope2.toString();
                String msg = String.format( "expected '%s' to win\n" + parser.dump( root ), expected );

                root = transform( root );
                msg += "\ntransformed:\n" + parser.dump( root );

                expectScope( msg, expected, root, 0 );
                expectScope( msg, expected, root, 1 );
            }
        }
    }

}
