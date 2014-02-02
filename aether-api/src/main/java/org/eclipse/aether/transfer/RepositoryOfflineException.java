/*******************************************************************************
 * Copyright (c) 2012, 2014 Sonatype, Inc.
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
 * Thrown when a transfer could not be performed because a remote repository is not accessible in offline mode.
 */
public class RepositoryOfflineException
    extends RepositoryException
{

    private final transient RemoteRepository repository;

    private static String getMessage( RemoteRepository repository )
    {
        if ( repository == null )
        {
            return "Cannot access remote repositories in offline mode";
        }
        else
        {
            return "Cannot access " + repository.getId() + " (" + repository.getUrl() + ") in offline mode";
        }
    }

    /**
     * Creates a new exception with the specified repository.
     * 
     * @param repository The inaccessible remote repository, may be {@code null}.
     */
    public RepositoryOfflineException( RemoteRepository repository )
    {
        super( getMessage( repository ) );
        this.repository = repository;
    }

    /**
     * Creates a new exception with the specified repository and detail message.
     * 
     * @param repository The inaccessible remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public RepositoryOfflineException( RemoteRepository repository, String message )
    {
        super( message );
        this.repository = repository;
    }

    /**
     * Gets the remote repository that could not be accessed due to offline mode.
     * 
     * @return The inaccessible remote repository or {@code null} if unknown.
     */
    public RemoteRepository getRepository()
    {
        return repository;
    }

}
