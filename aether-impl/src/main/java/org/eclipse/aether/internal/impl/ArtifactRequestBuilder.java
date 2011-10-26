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
package org.eclipse.aether.internal.impl;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.resolution.ArtifactRequest;

/**
 */
class ArtifactRequestBuilder
    implements DependencyVisitor
{

    private final RequestTrace trace;

    private List<ArtifactRequest> requests;

    public ArtifactRequestBuilder( RequestTrace trace )
    {
        this.trace = trace;
        this.requests = new ArrayList<ArtifactRequest>();
    }

    public List<ArtifactRequest> getRequests()
    {
        return requests;
    }

    public boolean visitEnter( DependencyNode node )
    {
        if ( node.getDependency() != null )
        {
            ArtifactRequest request = new ArtifactRequest( node );
            request.setTrace( trace );
            requests.add( request );
        }

        return true;
    }

    public boolean visitLeave( DependencyNode node )
    {
        return true;
    }

}
