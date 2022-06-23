package org.apache.maven.resolver.util.graph.transformer;

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

import org.apache.maven.resolver.collection.DependencyGraphTransformer;
import org.apache.maven.resolver.graph.DependencyNode;
import org.apache.maven.resolver.internal.test.util.DependencyGraphParser;
import org.junit.Test;

public class SimpleOptionalitySelectorTest
    extends AbstractDependencyGraphTransformerTest
{

    @Override
    protected DependencyGraphTransformer newTransformer()
    {
        return new ConflictResolver( new NearestVersionSelector(), new JavaScopeSelector(),
                                     new SimpleOptionalitySelector(), new JavaScopeDeriver() );
    }

    @Override
    protected DependencyGraphParser newParser()
    {
        return new DependencyGraphParser( "transformer/optionality-selector/" );
    }

    @Test
    public void testDeriveOptionality()
        throws Exception
    {
        DependencyNode root = parseResource( "derive.txt" );
        assertSame( root, transform( root ) );

        assertEquals( 2, root.getChildren().size() );
        assertTrue( root.getChildren().get( 0 ).getDependency().isOptional() );
        assertTrue( root.getChildren().get( 0 ).getChildren().get( 0 ).getDependency().isOptional() );
        assertFalse( root.getChildren().get( 1 ).getDependency().isOptional() );
        assertFalse( root.getChildren().get( 1 ).getChildren().get( 0 ).getDependency().isOptional() );
    }

    @Test
    public void testResolveOptionalityConflict_NonOptionalWins()
        throws Exception
    {
        DependencyNode root = parseResource( "conflict.txt" );
        assertSame( root, transform( root ) );

        assertEquals( 2, root.getChildren().size() );
        assertTrue( root.getChildren().get( 0 ).getDependency().isOptional() );
        assertFalse( root.getChildren().get( 0 ).getChildren().get( 0 ).getDependency().isOptional() );
    }

    @Test
    public void testResolveOptionalityConflict_DirectDeclarationWins()
        throws Exception
    {
        DependencyNode root = parseResource( "conflict-direct-dep.txt" );
        assertSame( root, transform( root ) );

        assertEquals( 2, root.getChildren().size() );
        assertTrue( root.getChildren().get( 1 ).getDependency().isOptional() );
    }

}
