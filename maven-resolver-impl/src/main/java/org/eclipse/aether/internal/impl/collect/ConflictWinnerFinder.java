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

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.visitor.CloningDependencyVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Objects;

import static org.eclipse.aether.internal.impl.collect.DependencyResolveReconciler.ga;
import static org.eclipse.aether.internal.impl.collect.DependencyResolveReconciler.gav;

/**
 * Find the conflict winner by transforming a cloned dependency graph
 */
public class ConflictWinnerFinder
{

    private static final Logger LOGGER = LoggerFactory.getLogger( ConflictWinnerFinder.class );

    /**
     * Find artifacts that conflicts with the winner. Only artifacts resolved before winner are considered as
     * conflicts.
     *
     * @return
     */
    Collection<Conflict> getVersionConflicts( RepositorySystemSession session, CollectResult result )
            throws DependencyCollectionException
    {
        LinkedHashSet<Conflict> conflicts = new LinkedHashSet<>();
        HashMap<String, ArrayList<Conflict>> conflictMap = resolveConflictWinners( session, result );
        if ( conflictMap == null )
        {
            return conflicts;
        }

        for ( String key : conflictMap.keySet() )
        {
            Collection<Conflict> col = conflictMap.get( key );
            if ( col != null && col.size() > 1 ) // more than ONE
            {
                Conflict[] array = col.toArray( new Conflict[0] );

                //find winner
                Artifact winner = null;
                int index = 0;
                for ( int i = 0; i < array.length; i++ )
                {
                    Conflict cur = array[i];
                    if ( cur.conflictedWith == null )
                    {
                        winner = cur.artifact;
                        index = i;
                        break;
                    }
                }

                //find conflicts: resolved before winner && version not equals
                for ( int i = 0; i < array.length; i++ )
                {
                    if ( i < index )
                    {
                        if ( LOGGER.isDebugEnabled() )
                        {
                            LOGGER.debug( "Found dependency: {} that conflicts with: {} ", array[i], winner );
                        }
                        conflicts.add( array[i] );
                    }
                }
            }
        }

        return conflicts;
    }

    /**
     * Use the ConflictResolver to find out all conflict winners based on a cloned dependency graph
     *
     * @param session
     * @param result
     * @return
     * @throws DependencyCollectionException
     */
    private HashMap<String, ArrayList<Conflict>> resolveConflictWinners( RepositorySystemSession session,
                                                                         CollectResult result )
            throws DependencyCollectionException
    {
        long start = System.nanoTime();

        DefaultRepositorySystemSession cloned = new DefaultRepositorySystemSession( session );
        // set as verbose so that the winner will be recorded in the DependencyNode.getData
        cloned.setConfigProperty( ConflictResolver.CONFIG_PROP_VERBOSE, true );
        DependencyGraphTransformer transformer = session.getDependencyGraphTransformer();
        if ( transformer == null )
        {
            return null;
        }

        //clone graph
        CloningDependencyVisitor vis = new CloningDependencyVisitor();
        DependencyNode root = result.getRoot();
        root.accept( vis );
        root = vis.getRootNode();

        //this part copied from DefaultDependencyCollector
        try
        {
            DefaultDependencyGraphTransformationContext context =
                    new DefaultDependencyGraphTransformationContext( cloned );
            root = transformer.transformGraph( root, context );
        }
        catch ( RepositoryException e )
        {
            result.addException( e );
        }

        if ( !result.getExceptions().isEmpty() )
        {
            throw new DependencyCollectionException( result );
        }

        DependencyConflictWinnersVisitor conflicts = new DependencyConflictWinnersVisitor();
        root.accept( conflicts );

        if ( LOGGER.isDebugEnabled() )
        {
            LOGGER.debug( "Finished to resolve conflict winners in : {} ", ( System.nanoTime() - start ) );
        }
        return conflicts.conflictMap;
    }


    //find out all conflict winners
    static class DependencyConflictWinnersVisitor
            implements DependencyVisitor
    {
        /**
         * The version conflicts of dependencies, conflicts were put to the map following the resolve sequence. ga ->
         * conflict
         */
        final HashMap<String, ArrayList<Conflict>> conflictMap = new HashMap<>();

        public boolean visitEnter( DependencyNode node )
        {
            Artifact a = node.getArtifact();
            Conflict conflict = new Conflict( a );
            conflictMap.computeIfAbsent( ga( a ), k -> new ArrayList<>() ).add( conflict );
            DependencyNode winner = (DependencyNode) node.getData().get( ConflictResolver.NODE_DATA_WINNER );
            if ( winner != null )
            {
                if ( !ArtifactIdUtils.equalsId( a, winner.getArtifact() ) )
                {
                    conflict.conflictedWith = winner.getArtifact();
                }
            }
            return true;
        }


        public boolean visitLeave( DependencyNode node )
        {
            return true;
        }
    }

    static class Conflict
    {
        Artifact artifact;
        Artifact conflictedWith;
        String gav;
        String ga;
        String version;
        private final int hashCode;

        Conflict( Artifact artifact )
        {
            this.artifact = artifact;
            this.version = artifact.getVersion();
            this.ga = ga( artifact );
            this.gav = gav( artifact );

            hashCode = Objects.hash( this.artifact );
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( obj == this )
            {
                return true;
            }
            else if ( !( obj instanceof Conflict ) )
            {
                return false;
            }
            Conflict that = (Conflict) obj;
            return Objects.equals( artifact, that.artifact );
        }

        @Override
        public int hashCode()
        {
            return hashCode;
        }

        @Override
        public String toString()
        {
            return "{"
                    + "artifact="
                    + artifact
                    + ( conflictedWith == null ? "" : ", conflicted with: " + conflictedWith )
                    + '}';
        }
    }
}
