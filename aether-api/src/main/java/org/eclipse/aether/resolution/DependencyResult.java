/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.resolution;

import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyCycle;
import org.eclipse.aether.graph.DependencyNode;

/**
 * The result of a dependency resolution request.
 * 
 * @see RepositorySystem#resolveDependencies(RepositorySystemSession, DependencyRequest)
 */
public final class DependencyResult
{

    private final DependencyRequest request;

    private DependencyNode root;

    private List<DependencyCycle> cycles;

    private List<Exception> collectExceptions;

    private List<ArtifactResult> artifactResults;

    /**
     * Creates a new result for the specified request.
     * 
     * @param request The resolution request, must not be {@code null}.
     */
    public DependencyResult( DependencyRequest request )
    {
        if ( request == null )
        {
            throw new IllegalArgumentException( "dependency request has not been specified" );
        }
        this.request = request;
        root = request.getRoot();
        cycles = Collections.emptyList();
        collectExceptions = Collections.emptyList();
        artifactResults = Collections.emptyList();
    }

    /**
     * Gets the resolution request that was made.
     * 
     * @return The resolution request, never {@code null}.
     */
    public DependencyRequest getRequest()
    {
        return request;
    }

    /**
     * Gets the root node of the resolved dependency graph. Note that this dependency graph might be
     * incomplete/unfinished in case of {@link #getCollectExceptions()} indicating errors during its calculation.
     * 
     * @return The root node of the resolved dependency graph or {@code null} if none.
     */
    public DependencyNode getRoot()
    {
        return root;
    }

    /**
     * Sets the root node of the resolved dependency graph.
     * 
     * @param root The root node of the resolved dependency graph, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public DependencyResult setRoot( DependencyNode root )
    {
        this.root = root;
        return this;
    }

    /**
     * Gets the dependency cycles that were encountered while building the dependency graph. Note that dependency cycles
     * will only be reported here if the underlying request was created from a
     * {@link org.eclipse.aether.collection.CollectRequest CollectRequest}. If the underlying {@link DependencyRequest}
     * was created from an existing dependency graph, information about cycles will not be available in this result.
     * 
     * @return The dependency cycles in the (raw) graph, never {@code null}.
     */
    public List<DependencyCycle> getCycles()
    {
        return cycles;
    }

    /**
     * Records the specified dependency cycles while building the dependency graph.
     * 
     * @param cycles The dependency cycles to record, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public DependencyResult setCycles( List<DependencyCycle> cycles )
    {
        if ( cycles == null )
        {
            this.cycles = Collections.emptyList();
        }
        else
        {
            this.cycles = cycles;
        }
        return this;
    }

    /**
     * Gets the exceptions that occurred while building the dependency graph.
     * 
     * @return The exceptions that occurred, never {@code null}.
     */
    public List<Exception> getCollectExceptions()
    {
        return collectExceptions;
    }

    /**
     * Records the specified exceptions while building the dependency graph.
     * 
     * @param exceptions The exceptions to record, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public DependencyResult setCollectExceptions( List<Exception> exceptions )
    {
        if ( exceptions == null )
        {
            this.collectExceptions = Collections.emptyList();
        }
        else
        {
            this.collectExceptions = exceptions;
        }
        return this;
    }

    /**
     * Gets the resolution results for the dependency artifacts that matched {@link DependencyRequest#getFilter()}.
     * 
     * @return The resolution results for the dependency artifacts, never {@code null}.
     */
    public List<ArtifactResult> getArtifactResults()
    {
        return artifactResults;
    }

    /**
     * Sets the resolution results for the artifacts that matched {@link DependencyRequest#getFilter()}.
     * 
     * @param results The resolution results for the artifacts, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public DependencyResult setArtifactResults( List<ArtifactResult> results )
    {
        if ( results == null )
        {
            this.artifactResults = Collections.emptyList();
        }
        else
        {
            this.artifactResults = results;
        }
        return this;
    }

    @Override
    public String toString()
    {
        return String.valueOf( artifactResults );
    }

}
