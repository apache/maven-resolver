/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.test.util;

import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/* *
 */
public class ArtifactDescription
{

    private List<RemoteRepository> repositories;

    private List<Dependency> managedDependencies;

    private List<Dependency> dependencies;

    private List<Artifact> relocations;

    ArtifactDescription( List<Artifact> relocations, List<Dependency> dependencies,
                         List<Dependency> managedDependencies, List<RemoteRepository> repositories )
    {
        this.relocations = relocations;
        this.dependencies = dependencies;
        this.managedDependencies = managedDependencies;
        this.repositories = repositories;
    }

    public List<Artifact> getRelocations()
    {
        return relocations;
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
