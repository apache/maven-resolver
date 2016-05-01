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
