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
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.util.ConfigUtils;

class Results
{

    private final CollectResult result;

    final int maxExceptions;

    final int maxCycles;

    String errorPath;

    public Results( CollectResult result, RepositorySystemSession session )
    {
        this.result = result;
        this.maxExceptions = ConfigUtils.getInteger( session, 50, DefaultDependencyCollector.CONFIG_PROP_MAX_EXCEPTIONS );
        this.maxCycles = ConfigUtils.getInteger( session, 10, DefaultDependencyCollector.CONFIG_PROP_MAX_CYCLES );
    }

    public void addException( Dependency dependency, Exception e, NodeStack nodes )
    {
        if ( maxExceptions < 0 || result.getExceptions().size() < maxExceptions )
        {
            result.addException( e );
            if ( errorPath == null )
            {
                StringBuilder buffer = new StringBuilder( 256 );
                for ( int i = 0; i < nodes.size(); i++ )
                {
                    if ( buffer.length() > 0 )
                    {
                        buffer.append( " -> " );
                    }
                    Dependency dep = nodes.get( i ).getDependency();
                    if ( dep != null )
                    {
                        buffer.append( dep.getArtifact() );
                    }
                }
                if ( buffer.length() > 0 )
                {
                    buffer.append( " -> " );
                }
                buffer.append( dependency.getArtifact() );
                errorPath = buffer.toString();
            }
        }
    }

    public void addCycle( NodeStack nodes, int cycleEntry, Dependency dependency )
    {
        if ( maxCycles < 0 || result.getCycles().size() < maxCycles )
        {
            result.addCycle( new DefaultDependencyCycle( nodes, cycleEntry, dependency ) );
        }
    }

}