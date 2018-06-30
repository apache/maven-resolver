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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;

class Args
{

    final RepositorySystemSession session;

    final boolean ignoreRepos;

    final boolean premanagedState;

    final RequestTrace trace;

    final DataPool pool;

    final NodeStack nodes;

    final DefaultDependencyCollectionContext collectionContext;

    final DefaultVersionFilterContext versionContext;

    final CollectRequest request;


    Args( RepositorySystemSession session, RequestTrace trace, DataPool pool, NodeStack nodes,
                 DefaultDependencyCollectionContext collectionContext, DefaultVersionFilterContext versionContext,
                 CollectRequest request )
    {
        this.session = session;
        this.request = request;
        this.ignoreRepos = session.isIgnoreArtifactDescriptorRepositories();
        this.premanagedState = ConfigUtils.getBoolean( session, false, DependencyManagerUtils.CONFIG_PROP_VERBOSE );
        this.trace = trace;
        this.pool = pool;
        this.nodes = nodes;
        this.collectionContext = collectionContext;
        this.versionContext = versionContext;
    }

}