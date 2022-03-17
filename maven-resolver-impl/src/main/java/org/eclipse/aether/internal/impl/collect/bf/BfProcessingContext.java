package org.eclipse.aether.internal.impl.collect.bf;

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

import java.util.List;

import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;

final class BfProcessingContext
{
    final DependencySelector depSelector;
    final DependencyManager depManager;
    final DependencyTraverser depTraverser;
    final VersionFilter verFilter;
    final List<RemoteRepository> repositories;
    final List<Dependency> managedDependencies;

    /**
     * All parents of the dependency in the top > down order.
     */
    final List<DependencyNode> parents;
    final Dependency dependency;

    @SuppressWarnings( "checkstyle:parameternumber" )
    BfProcessingContext( DependencySelector depSelector,
                                 DependencyManager depManager,
                                 DependencyTraverser depTraverser,
                                 VersionFilter verFilter,
                                 List<RemoteRepository> repositories,
                                 List<Dependency> managedDependencies,
                                 List<DependencyNode> parents,
                                 Dependency dependency )
    {
        this.depSelector = depSelector;
        this.depManager = depManager;
        this.depTraverser = depTraverser;
        this.verFilter = verFilter;
        this.repositories = repositories;
        this.dependency = dependency;
        this.managedDependencies = managedDependencies;
        this.parents = parents;
    }

    BfProcessingContext withDependency( Dependency dependency )
    {
        return new BfProcessingContext( depSelector, depManager, depTraverser, verFilter, repositories,
                managedDependencies, parents, dependency );
    }

    DependencyNode getParent()
    {
        return parents.get( parents.size() - 1 );
    }
}
