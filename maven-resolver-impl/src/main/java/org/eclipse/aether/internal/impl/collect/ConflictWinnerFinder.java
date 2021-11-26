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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;

import static org.eclipse.aether.internal.impl.collect.DependencyResolveReconciler.ga;
import static org.eclipse.aether.internal.impl.collect.DependencyResolveReconciler.gav;

/**
 * Find the conflict winner by transforming a cloned dependency graph
 */
public class ConflictWinnerFinder
{

    private boolean verbose;

    private static final Logger LOGGER = LoggerFactory.getLogger( ConflictWinnerFinder.class );

    public ConflictWinnerFinder( boolean verbose )
    {
        this.verbose = verbose;
    }

    /**
     * Find artifacts that conflicts with the winner. Only artifacts resolved before winner are considered as
     * conflicts.
     *
     * @return
     */
    public Collection<Conflict> getVersionConflicts( RepositorySystemSession session, CollectResult res )
            throws DependencyCollectionException
    {
        LinkedHashSet<Conflict> conflicts = new LinkedHashSet<>();
        Multimap<String, Conflict> conflictMap = resolveConflictWinners( session, res );
        if ( conflictMap == null )
        {
            return conflicts;
        }

        for ( String key : conflictMap.keySet() )
        {
            Collection<Conflict> col = conflictMap.get( key );
            if ( col != null && col.size() > 1 )
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
                        if ( verbose )
                        {
                            LOGGER.info( "Found dependency: {} that conflicts with: {} ", array[i], winner );
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
     * @param res
     * @return
     * @throws DependencyCollectionException
     */
    private Multimap<String, Conflict> resolveConflictWinners( RepositorySystemSession session, CollectResult res )
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
        DependencyNode root = res.getRoot();
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
            res.addException( e );
        }

        if ( !res.getExceptions().isEmpty() )
        {
            throw new DependencyCollectionException( res );
        }

        DependencyConflictWinnersVisitor conflicts = new DependencyConflictWinnersVisitor();
        root.accept( conflicts );

        if ( verbose )
        {
            LOGGER.info( "Finished to resolve conflict winners in : {} ", ( System.nanoTime() - start ) );
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
        Multimap<String, Conflict> conflictMap = LinkedHashMultimap.create();

        public boolean visitEnter( DependencyNode node )
        {
            Artifact a = node.getArtifact();
            Conflict conflict = new Conflict( a );
            conflictMap.put( ga( a ), conflict );
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
