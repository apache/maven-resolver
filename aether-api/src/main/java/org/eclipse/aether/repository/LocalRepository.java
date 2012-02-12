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

import java.io.File;

/**
 * A repository on the local file system used to cache contents of remote repositories and to store locally installed
 * artifacts. Note that this class merely describes such a repository, actual access to the contained artifacts is
 * handled by a {@link LocalRepositoryManager} which is usually determined from the {@link #getContentType() type} of
 * the repository.
 */
public final class LocalRepository
    implements ArtifactRepository
{

    private final File basedir;

    private final String type;

    /**
     * Creates a new local repository with the specified base directory and unknown type.
     * 
     * @param basedir The base directory of the repository, may be {@code null}.
     */
    public LocalRepository( String basedir )
    {
        this( ( basedir != null ) ? new File( basedir ) : null, "" );
    }

    /**
     * Creates a new local repository with the specified base directory and unknown type.
     * 
     * @param basedir The base directory of the repository, may be {@code null}.
     */
    public LocalRepository( File basedir )
    {
        this( basedir, "" );
    }

    /**
     * Creates a new local repository with the specified properties.
     * 
     * @param basedir The base directory of the repository, may be {@code null}.
     * @param type The type of the repository, may be {@code null}.
     */
    public LocalRepository( File basedir, String type )
    {
        this.basedir = basedir;
        this.type = ( type != null ) ? type : "";
    }

    public String getContentType()
    {
        return type;
    }

    public String getId()
    {
        return "local";
    }

    /**
     * Gets the base directory of the repository.
     * 
     * @return The base directory or {@code null} if none.
     */
    public File getBasedir()
    {
        return basedir;
    }

    @Override
    public String toString()
    {
        return getBasedir() + " (" + getContentType() + ")";
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null || !getClass().equals( obj.getClass() ) )
        {
            return false;
        }

        LocalRepository that = (LocalRepository) obj;

        return eq( basedir, that.basedir ) && eq( type, that.type );
    }

    private static <T> boolean eq( T s1, T s2 )
    {
        return s1 != null ? s1.equals( s2 ) : s2 == null;
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + hash( basedir );
        hash = hash * 31 + hash( type );
        return hash;
    }

    private static int hash( Object obj )
    {
        return obj != null ? obj.hashCode() : 0;
    }

}
