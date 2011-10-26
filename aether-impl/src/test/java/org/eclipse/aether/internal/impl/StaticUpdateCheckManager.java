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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.UpdateCheck;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;

class StaticUpdateCheckManager
    implements UpdateCheckManager
{

    private boolean checkRequired;

    public StaticUpdateCheckManager( boolean checkRequired )
    {
        this.checkRequired = checkRequired;
    }

    public void touchMetadata( RepositorySystemSession session, UpdateCheck<Metadata, MetadataTransferException> check )
    {
    }

    public void touchArtifact( RepositorySystemSession session, UpdateCheck<Artifact, ArtifactTransferException> check )
    {
    }

    public String getEffectiveUpdatePolicy( RepositorySystemSession session, String policy1, String policy2 )
    {
        return policy1;
    }

    public void checkMetadata( RepositorySystemSession session, UpdateCheck<Metadata, MetadataTransferException> check )
    {
        check.setRequired( checkRequired );

        if ( !check.isRequired() && !check.getFile().isFile() )
        {
            check.setException( new MetadataNotFoundException( check.getItem(), check.getRepository() ) );
        }
    }

    public void checkArtifact( RepositorySystemSession session, UpdateCheck<Artifact, ArtifactTransferException> check )
    {
        check.setRequired( checkRequired );

        if ( !check.isRequired() && !check.getFile().isFile() )
        {
            check.setException( new ArtifactNotFoundException( check.getItem(), check.getRepository() ) );
        }
    }

    public boolean isUpdatedRequired( RepositorySystemSession session, long lastModified, String policy )
    {
        return checkRequired;
    }

}
