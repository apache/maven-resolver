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
package org.eclipse.aether.internal.test.util;

import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 */
class ArtifactDescription
{

    private List<RemoteRepository> repositories;

    private List<Dependency> managedDependencies;

    private List<Dependency> dependencies;

    private Artifact relocation;

    ArtifactDescription( Artifact relocation, List<Dependency> dependencies, List<Dependency> managedDependencies,
                         List<RemoteRepository> repositories )
    {
        this.relocation = relocation;
        this.dependencies = dependencies;
        this.managedDependencies = managedDependencies;
        this.repositories = repositories;
    }

    public Artifact getRelocation()
    {
        return relocation;
    }

    public List<RemoteRepository> getRepositories()
    {
        return repositories;
    }

    public List<Dependency> getManagedDependencies()
    {
        return managedDependencies;
    }

    public List<Dependency> getDependencies()
    {
        return dependencies;
    }

}
