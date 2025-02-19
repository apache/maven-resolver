/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.collection;

import java.util.List;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

/**
 * A trace data object revealing collect step (while executing {@link CollectRequest}.
 *
 * @see org.eclipse.aether.RequestTrace
 * @since 1.8.1
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface CollectStepData {
    /**
     * Returns the context of collection. Never {@code null}.
     */
    String getContext();

    /**
     * Returns the path of dependency nodes that led collector to current node returned by {@link #getNode()}.
     * Never {@code null}.
     */
    List<DependencyNode> getPath();

    /**
     * Returns the current node being collected. Never {@code null}.
     */
    Dependency getNode();
}
