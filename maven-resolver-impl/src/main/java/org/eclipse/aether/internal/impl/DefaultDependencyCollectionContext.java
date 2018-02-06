package org.eclipse.aether.internal.impl;

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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.graph.Dependency;

/**
 * @see DefaultDependencyCollector
 */
final class DefaultDependencyCollectionContext
    implements DependencyCollectionContext
{

    private final RepositorySystemSession session;

    private Artifact artifact;

    private Dependency dependency;

    private List<Dependency> managedDependencies;

    DefaultDependencyCollectionContext( RepositorySystemSession session, Artifact artifact,
                                               Dependency dependency, List<Dependency> managedDependencies )
    {
        this.session = session;
        this.artifact = ( dependency != null ) ? dependency.getArtifact() : artifact;
        this.dependency = dependency;
        this.managedDependencies = managedDependencies;
    }

    public RepositorySystemSession getSession()
    {
        return session;
    }

    public Artifact getArtifact()
    {
        return artifact;
    }

    public Dependency getDependency()
    {
        return dependency;
    }

    public List<Dependency> getManagedDependencies()
    {
        return managedDependencies;
    }

    public void set( Dependency dependency, List<Dependency> managedDependencies )
    {
        artifact = dependency.getArtifact();
        this.dependency = dependency;
        this.managedDependencies = managedDependencies;
    }

    @Override
    public String toString()
    {
        return String.valueOf( getDependency() );
    }

}
