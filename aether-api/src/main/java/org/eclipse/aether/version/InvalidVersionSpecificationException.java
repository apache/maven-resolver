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
package org.eclipse.aether.version;

import org.eclipse.aether.RepositoryException;

/**
 * Thrown when a version or version range could not be parsed.
 */
public class InvalidVersionSpecificationException
    extends RepositoryException
{

    private final String version;

    /**
     * Creates a new exception with the specified version and detail message.
     * 
     * @param version The invalid version specification, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public InvalidVersionSpecificationException( String version, String message )
    {
        super( message );
        this.version = version;
    }

    /**
     * Creates a new exception with the specified version and cause.
     * 
     * @param version The invalid version specification, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public InvalidVersionSpecificationException( String version, Throwable cause )
    {
        super( "Could not parse version specification " + version + getMessage( ": ", cause ), cause );
        this.version = version;
    }

    /**
     * Creates a new exception with the specified version, detail message and cause.
     * 
     * @param version The invalid version specification, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public InvalidVersionSpecificationException( String version, String message, Throwable cause )
    {
        super( message, cause );
        this.version = version;
    }

    /**
     * Gets the version or version range that could not be parsed.
     * 
     * @return The invalid version specification or {@code null} if unknown.
     */
    public String getVersion()
    {
        return version;
    }

}
