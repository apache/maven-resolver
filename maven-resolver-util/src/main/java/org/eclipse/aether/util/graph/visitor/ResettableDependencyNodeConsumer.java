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
package org.eclipse.aether.util.graph.visitor;

import java.util.function.Consumer;

import org.eclipse.aether.graph.DependencyNode;

/**
 * Consumer to be used with {@link AbstractVisitor} implementations.
 *
 * @since TBD
 */
public interface ResettableDependencyNodeConsumer extends Consumer<DependencyNode> {
    /**
     * This call is required to be able to implement wildly different strategies. If this method is invoked,
     * the instance should "reset" itself, "forget" all the invocations happened so far. Some strategies, most
     * notable those retaining states like "level" one have to make use of this call due lack of calling state.
     */
    void reset();
}
