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
package org.eclipse.aether.transfer;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Thrown in case of an unsupported remote repository type.
 */
public class NoRepositoryConnectorException
    extends RepositoryException
{

    private final RemoteRepository repository;

    public NoRepositoryConnectorException( RemoteRepository repository )
    {
        this( repository, toMessage( repository ) );
    }

    public NoRepositoryConnectorException( RemoteRepository repository, String message )
    {
        super( message );

        this.repository = repository;
    }

    public NoRepositoryConnectorException( RemoteRepository repository, Throwable cause )
    {
        this( repository, toMessage( repository ), cause );
    }

    public NoRepositoryConnectorException( RemoteRepository repository, String message, Throwable cause )
    {
        super( message, cause );

        this.repository = repository;
    }

    private static String toMessage( RemoteRepository repository )
    {
        if ( repository != null )
        {
            return "No connector available to access repository " + repository.getId() + " (" + repository.getUrl()
                + ") of type " + repository.getContentType();
        }
        else
        {
            return "No connector available to access repository";
        }
    }

    public RemoteRepository getRepository()
    {
        return repository;
    }

}
