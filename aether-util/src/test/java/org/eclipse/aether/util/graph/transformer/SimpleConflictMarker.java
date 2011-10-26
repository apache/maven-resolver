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
package org.eclipse.aether.util.graph.transformer;

import java.util.IdentityHashMap;
import java.util.Map;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.transformer.TransformationContextKeys;

/**
 * Set "groupId:artId:classifier:extension" as conflict marker for every node.
 */
class SimpleConflictMarker
    implements DependencyGraphTransformer
{

    public DependencyNode transformGraph( DependencyNode node, DependencyGraphTransformationContext context )
        throws RepositoryException
    {
        @SuppressWarnings( "unchecked" )
        Map<DependencyNode, Object> conflictIds =
            (Map<DependencyNode, Object>) context.get( TransformationContextKeys.CONFLICT_IDS );
        if ( conflictIds == null )
        {
            conflictIds = new IdentityHashMap<DependencyNode, Object>();
            context.put( TransformationContextKeys.CONFLICT_IDS, conflictIds );
        }

        mark( node, conflictIds );

        return node;
    }

    private void mark( DependencyNode node, Map<DependencyNode, Object> conflictIds )
    {
        Dependency dependency = node.getDependency();
        if ( dependency != null )
        {
            Artifact artifact = dependency.getArtifact();

            String key =
                String.format( "%s:%s:%s:%s", artifact.getGroupId(), artifact.getArtifactId(),
                               artifact.getClassifier(), artifact.getExtension() );

            if ( conflictIds.put( node, key ) != null )
            {
                return;
            }
        }

        for ( DependencyNode child : node.getChildren() )
        {
            mark( child, conflictIds );
        }
    }

}
