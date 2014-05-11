/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
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
import java.util.Collection;
import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.MetadataGenerator;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.resolution.ResolutionErrorPolicyRequest;
import org.eclipse.aether.transfer.RepositoryOfflineException;

/**
 * Internal utility methods.
 */
final class Utils
{

    public static PrioritizedComponents<MetadataGeneratorFactory> sortMetadataGeneratorFactories( RepositorySystemSession session,
                                                                                                  Collection<? extends MetadataGeneratorFactory> factories )
    {
        PrioritizedComponents<MetadataGeneratorFactory> result =
            new PrioritizedComponents<MetadataGeneratorFactory>( session );
        for ( MetadataGeneratorFactory factory : factories )
        {
            result.add( factory, factory.getPriority() );
        }
        return result;
    }

    public static List<Metadata> prepareMetadata( List<? extends MetadataGenerator> generators,
                                                  List<? extends Artifact> artifacts )
    {
        List<Metadata> metadatas = new ArrayList<Metadata>();

        for ( MetadataGenerator generator : generators )
        {
            metadatas.addAll( generator.prepare( artifacts ) );
        }

        return metadatas;
    }

    public static List<Metadata> finishMetadata( List<? extends MetadataGenerator> generators,
                                                 List<? extends Artifact> artifacts )
    {
        List<Metadata> metadatas = new ArrayList<Metadata>();

        for ( MetadataGenerator generator : generators )
        {
            metadatas.addAll( generator.finish( artifacts ) );
        }

        return metadatas;
    }

    public static <T> List<T> combine( Collection<? extends T> first, Collection<? extends T> second )
    {
        List<T> result = new ArrayList<T>( first.size() + second.size() );
        result.addAll( first );
        result.addAll( second );
        return result;
    }

    public static int getPolicy( RepositorySystemSession session, Artifact artifact, RemoteRepository repository )
    {
        ResolutionErrorPolicy rep = session.getResolutionErrorPolicy();
        if ( rep == null )
        {
            return ResolutionErrorPolicy.CACHE_DISABLED;
        }
        return rep.getArtifactPolicy( session, new ResolutionErrorPolicyRequest<Artifact>( artifact, repository ) );
    }

    public static int getPolicy( RepositorySystemSession session, Metadata metadata, RemoteRepository repository )
    {
        ResolutionErrorPolicy rep = session.getResolutionErrorPolicy();
        if ( rep == null )
        {
            return ResolutionErrorPolicy.CACHE_DISABLED;
        }
        return rep.getMetadataPolicy( session, new ResolutionErrorPolicyRequest<Metadata>( metadata, repository ) );
    }

    public static void appendClassLoader( StringBuilder buffer, Object component )
    {
        ClassLoader loader = component.getClass().getClassLoader();
        if ( loader != null && !loader.equals( Utils.class.getClassLoader() ) )
        {
            buffer.append( " from " ).append( loader );
        }
    }

    public static void checkOffline( RepositorySystemSession session, OfflineController offlineController,
                                     RemoteRepository repository )
        throws RepositoryOfflineException
    {
        if ( session.isOffline() )
        {
            offlineController.checkOffline( session, repository );
        }
    }

}
