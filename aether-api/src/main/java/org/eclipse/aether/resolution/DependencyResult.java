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
package org.eclipse.aether.resolution;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
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
        this.root = request.getRoot();
        this.collectExceptions = new ArrayList<Exception>( 2 );
        this.artifactResults = new ArrayList<ArtifactResult>( 2 );
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
        this.collectExceptions = ( exceptions != null ) ? exceptions : new ArrayList<Exception>();
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
        this.artifactResults = ( results != null ) ? results : new ArrayList<ArtifactResult>();
        return this;
    }

    @Override
    public String toString()
    {
        return String.valueOf( artifactResults );
    }

}
