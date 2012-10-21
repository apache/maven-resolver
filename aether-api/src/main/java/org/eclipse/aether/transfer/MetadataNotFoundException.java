/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.transfer;

import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Thrown when metadata was not found in a particular repository.
 */
public class MetadataNotFoundException
    extends MetadataTransferException
{

    public MetadataNotFoundException( Metadata metadata, LocalRepository repository )
    {
        super( metadata, null, "Could not find metadata " + metadata + getString( " in ", repository ) );
    }

    private static String getString( String prefix, LocalRepository repository )
    {
        if ( repository == null )
        {
            return "";
        }
        else
        {
            return prefix + repository.getId() + " (" + repository.getBasedir() + ")";
        }
    }

    public MetadataNotFoundException( Metadata metadata, RemoteRepository repository )
    {
        super( metadata, repository, "Could not find metadata " + metadata + getString( " in ", repository ) );
    }

    public MetadataNotFoundException( Metadata metadata, RemoteRepository repository, String message )
    {
        super( metadata, repository, message );
    }

    public MetadataNotFoundException( Metadata metadata, RemoteRepository repository, String message, Throwable cause )
    {
        super( metadata, repository, message, cause );
    }

}
