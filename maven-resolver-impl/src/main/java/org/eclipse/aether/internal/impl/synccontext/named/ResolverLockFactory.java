package org.eclipse.aether.internal.impl.synccontext.named;

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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.spi.synccontext.SyncContextHint;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Component mapping lock names to passed in artifacts and metadata as required.
 */
public final class ResolverLockFactory
{
    private final NameMapper nameMapper;

    private final NamedLockFactory namedLockFactory;

    public ResolverLockFactory( final NameMapper nameMapper, final NamedLockFactory namedLockFactory )
    {
        this.nameMapper = Objects.requireNonNull( nameMapper );
        this.namedLockFactory = Objects.requireNonNull( namedLockFactory );
    }

    public Collection<ResolverLock> resolverLocks( final RepositorySystemSession session,
                                                   final boolean shared,
                                                   final Collection<? extends Artifact> artifacts,
                                                   final Collection<? extends Metadata> metadatas )
    {
        TreeSet<ResolverLock> result = new TreeSet<>( Comparator.comparing( ResolverLock::key ) );
        boolean effectiveShared = shared;
        boolean mayOverride = SyncContextHint.Scope.RESOLVE.equals( SyncContextHint.SCOPE.get() );

        if ( artifacts != null )
        {
            for ( Artifact artifact : artifacts )
            {
                NamedLock namedLock = namedLockFactory.getLock( nameMapper.nameLock( session, artifact ) );
                if ( mayOverride && !shared )
                {
                    effectiveShared = isArtifactAvailable( session, artifact );
                }
                result.add( new ResolverLock( artifact.toString(), namedLock, shared, effectiveShared ) );
            }
        }

        if ( metadatas != null )
        {
            for ( Metadata metadata : metadatas )
            {
                NamedLock namedLock = namedLockFactory.getLock( nameMapper.nameLock( session, metadata ) );
                if ( mayOverride && !shared )
                {
                    effectiveShared = isMetadataAvailable( session, metadata );
                }
                result.add( new ResolverLock( metadata.toString(), namedLock, shared, effectiveShared ) );
            }
        }

        return result;
    }

    private boolean isArtifactAvailable( final RepositorySystemSession session, final Artifact artifact )
    {
        LocalArtifactRequest request = new LocalArtifactRequest( artifact, null, null );
        LocalArtifactResult result = session.getLocalRepositoryManager().find( session, request );
        return result.getFile() != null;
    }

    private boolean isMetadataAvailable( final RepositorySystemSession session, final Metadata metadata )
    {
        LocalMetadataRequest request = new LocalMetadataRequest( metadata, null, null );
        LocalMetadataResult result = session.getLocalRepositoryManager().find( session, request );
        return result.getFile() != null;
    }

    /**
     * Performs a clean shut down of the factory.
     */
    public void shutdown()
    {
        namedLockFactory.shutdown();
    }
}
