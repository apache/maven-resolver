package org.eclipse.aether.internal.impl.collect;

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

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyCycle;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DefaultDependencyCycleTest
{
    private static final Dependency FOO_DEPENDENCY = new Dependency( new DefaultArtifact( "group-id:foo:1.0" ), "test" );
    private static final Dependency BAR_DEPENDENCY = new Dependency( new DefaultArtifact( "group-id:bar:1.0" ), "test" );

    @Test
    public void testToString()
    {
        List<DependencyNode> nodes = new ArrayList<>();
        nodes.add( new DefaultDependencyNode( FOO_DEPENDENCY ) );
        DependencyCycle cycle = new DefaultDependencyCycle( nodes, 1, BAR_DEPENDENCY );

        assertEquals( "group-id:foo:jar -> group-id:bar:jar", cycle.toString() );
    }
}