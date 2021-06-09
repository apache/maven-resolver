package org.eclipse.aether.internal.impl;

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
import org.eclipse.aether.impl.UpdateCheck;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;

import static java.util.Objects.requireNonNull;

class StaticUpdateCheckManager
    implements UpdateCheckManager
{

    private boolean checkRequired;

    private boolean localUpToDate;

    public StaticUpdateCheckManager( boolean checkRequired )
    {
        this( checkRequired, !checkRequired );
    }

    public StaticUpdateCheckManager( boolean checkRequired, boolean localUpToDate )
    {
        this.checkRequired = checkRequired;
        this.localUpToDate = localUpToDate;
    }

    public void touchMetadata( RepositorySystemSession session, UpdateCheck<Metadata, MetadataTransferException> check )
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( check, "check cannot be null" );
    }

    public void touchArtifact( RepositorySystemSession session, UpdateCheck<Artifact, ArtifactTransferException> check )
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( check, "check cannot be null" );
    }

    public void checkMetadata( RepositorySystemSession session, UpdateCheck<Metadata, MetadataTransferException> check )
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( check, "check cannot be null" );
        check.setRequired( checkRequired );

        if ( check.getLocalLastUpdated() != 0L && localUpToDate )
        {
            check.setRequired( false );
        }
        if ( !check.isRequired() && !check.getFile().isFile() )
        {
            check.setException( new MetadataNotFoundException( check.getItem(), check.getRepository() ) );
        }
    }

    public void checkArtifact( RepositorySystemSession session, UpdateCheck<Artifact, ArtifactTransferException> check )
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( check, "check cannot be null" );
        check.setRequired( checkRequired );

        if ( check.getLocalLastUpdated() != 0L && localUpToDate )
        {
            check.setRequired( false );
        }
        if ( !check.isRequired() && !check.getFile().isFile() )
        {
            check.setException( new ArtifactNotFoundException( check.getItem(), check.getRepository() ) );
        }
    }

}
