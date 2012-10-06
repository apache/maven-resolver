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
package org.eclipse.aether.repository;

import org.eclipse.aether.RepositoryException;

/**
 * Thrown in case of an unsupported local repository type.
 */
public class NoLocalRepositoryManagerException
    extends RepositoryException
{

    private final transient LocalRepository repository;

    public NoLocalRepositoryManagerException( LocalRepository repository )
    {
        this( repository, toMessage( repository ) );
    }

    public NoLocalRepositoryManagerException( LocalRepository repository, String message )
    {
        super( message );

        this.repository = repository;
    }

    public NoLocalRepositoryManagerException( LocalRepository repository, Throwable cause )
    {
        this( repository, toMessage( repository ), cause );
    }

    public NoLocalRepositoryManagerException( LocalRepository repository, String message, Throwable cause )
    {
        super( message, cause );

        this.repository = repository;
    }

    private static String toMessage( LocalRepository repository )
    {
        if ( repository != null )
        {
            return "No manager available for local repository (" + repository.getBasedir().getAbsolutePath()
                + ") of type " + repository.getContentType();
        }
        else
        {
            return "No connector available to access repository";
        }
    }

    public LocalRepository getRepository()
    {
        return repository;
    }

}
