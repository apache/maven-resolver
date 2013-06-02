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
package org.eclipse.aether.spi.connector.layout;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Thrown in case of an unsupported repository layout.
 */
public class NoRepositoryLayoutException
    extends RepositoryException
{

    private final transient RemoteRepository repository;

    public NoRepositoryLayoutException( RemoteRepository repository )
    {
        this( repository, toMessage( repository ) );
    }

    public NoRepositoryLayoutException( RemoteRepository repository, String message )
    {
        super( message );

        this.repository = repository;
    }

    public NoRepositoryLayoutException( RemoteRepository repository, Throwable cause )
    {
        this( repository, toMessage( repository ), cause );
    }

    public NoRepositoryLayoutException( RemoteRepository repository, String message, Throwable cause )
    {
        super( message, cause );

        this.repository = repository;
    }

    private static String toMessage( RemoteRepository repository )
    {
        if ( repository != null )
        {
            return "Unsupported repository layout used by repository " + repository.getUrl() + " with content type "
                + repository.getContentType();
        }
        else
        {
            return "Unsupported repository layout";
        }
    }

    public RemoteRepository getRepository()
    {
        return repository;
    }

}
