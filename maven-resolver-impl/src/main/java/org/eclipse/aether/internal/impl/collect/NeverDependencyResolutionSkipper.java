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

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DependencyResolutionSkipper;

import java.util.List;

/**
 * Skipper for Non-skip approach.
 */
final class NeverDependencyResolutionSkipper implements DependencyResolutionSkipper
{
    static final DependencyResolutionSkipper INSTANCE = new NeverDependencyResolutionSkipper();

    @Override
    public boolean skipResolution( DependencyNode node, List<DependencyNode> parents )
    {
        return false;
    }

    @Override
    public void cache( DependencyNode node, List<DependencyNode> parents )
    {

    }

    @Override
    public void report()
    {

    }
}
