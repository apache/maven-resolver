/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
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
 * Thrown in case of an unsupported transport protocol.
 */
public class NoTransporterException
    extends RepositoryException
{

    private final transient RemoteRepository repository;

    public NoTransporterException( RemoteRepository repository )
    {
        this( repository, toMessage( repository ) );
    }

    public NoTransporterException( RemoteRepository repository, String message )
    {
        super( message );

        this.repository = repository;
    }

    public NoTransporterException( RemoteRepository repository, Throwable cause )
    {
        this( repository, toMessage( repository ), cause );
    }

    public NoTransporterException( RemoteRepository repository, String message, Throwable cause )
    {
        super( message, cause );

        this.repository = repository;
    }

    private static String toMessage( RemoteRepository repository )
    {
        if ( repository != null )
        {
            return "No transporter available to access repository " + repository.getUrl();
        }
        else
        {
            return "No transporter available to access repository";
        }
    }

    public RemoteRepository getRepository()
    {
        return repository;
    }

}
