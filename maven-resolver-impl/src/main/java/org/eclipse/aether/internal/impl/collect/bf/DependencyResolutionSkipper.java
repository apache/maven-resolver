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

import org.eclipse.aether.graph.DependencyNode;

import java.util.List;

/**
 * A skipper that determines whether to skip resolving given node during the dependency collection.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 * @since 1.8.0
 */
public interface DependencyResolutionSkipper
{
    /**
     * Check whether the resolution of current node can be skipped before resolving.
     *
     * @param node    Current node
     * @param parents All parent nodes of current node
     *
     * @return {@code true} if the node can be skipped for resolution, {@code false} if resolution required.
     */
    boolean skipResolution( DependencyNode node, List<DependencyNode> parents );

    /**
     * Cache the resolution result when a node is resolved by @See DependencyCollector after resolution.
     *
     * @param node    Current node
     * @param parents All parent nodes of current node
     */
    void cache( DependencyNode node, List<DependencyNode> parents );

    /**
     * Print the skip/resolve status report for all nodes.
     */
    void report();

}
