/*******************************************************************************
 * Copyright (c) 2013, 2014 Sonatype, Inc.
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
 * Thrown in case of an unsupported repository layout.
 */
public class NoRepositoryLayoutException
    extends RepositoryException
{

    private final transient RemoteRepository repository;

    /**
     * Creates a new exception with the specified repository.
     * 
     * @param repository The remote repository whose layout is not supported, may be {@code null}.
     */
    public NoRepositoryLayoutException( RemoteRepository repository )
    {
        this( repository, toMessage( repository ) );
    }

    /**
     * Creates a new exception with the specified repository and detail message.
     * 
     * @param repository The remote repository whose layout is not supported, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public NoRepositoryLayoutException( RemoteRepository repository, String message )
    {
        super( message );
        this.repository = repository;
    }

    /**
     * Creates a new exception with the specified repository and cause.
     * 
     * @param repository The remote repository whose layout is not supported, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public NoRepositoryLayoutException( RemoteRepository repository, Throwable cause )
    {
        this( repository, toMessage( repository ), cause );
    }

    /**
     * Creates a new exception with the specified repository, detail message and cause.
     * 
     * @param repository The remote repository whose layout is not supported, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public NoRepositoryLayoutException( RemoteRepository repository, String message, Throwable cause )
    {
        super( message, cause );
        this.repository = repository;
    }

    private static String toMessage( RemoteRepository repository )
    {
        if ( repository != null )
        {
            return "Unsupported repository layout " + repository.getContentType();
        }
        else
        {
            return "Unsupported repository layout";
        }
    }

    /**
     * Gets the remote repository whose layout is not supported.
     * 
     * @return The unsupported remote repository or {@code null} if unknown.
     */
    public RemoteRepository getRepository()
    {
        return repository;
    }

}
