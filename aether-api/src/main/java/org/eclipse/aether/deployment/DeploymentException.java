/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.deployment;

import org.eclipse.aether.RepositoryException;

/**
 * Thrown in case of a deployment error like authentication failure.
 */
public class DeploymentException
    extends RepositoryException
{

    /**
     * Creates a new exception with the specified detail message.
     * 
     * @param message The detail message, may be {@code null}.
     */
    public DeploymentException( String message )
    {
        super( message );
    }

    /**
     * Creates a new exception with the specified detail message and cause.
     * 
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public DeploymentException( String message, Throwable cause )
    {
        super( message, cause );
    }

}
