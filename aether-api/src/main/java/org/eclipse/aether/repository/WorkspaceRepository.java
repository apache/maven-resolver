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
package org.eclipse.aether.repository;

import java.util.UUID;

/**
 * A repository backed by an IDE workspace or the output of a build session.
 */
public final class WorkspaceRepository
    implements ArtifactRepository
{

    private final String type;

    private final Object key;

    public WorkspaceRepository()
    {
        this( "workspace" );
    }

    public WorkspaceRepository( String type )
    {
        this( type, null );
    }

    public WorkspaceRepository( String type, Object key )
    {
        this.type = ( type != null ) ? type : "";
        this.key = ( key != null ) ? key : UUID.randomUUID().toString().replace( "-", "" );
    }

    public String getContentType()
    {
        return type;
    }

    public String getId()
    {
        return "workspace";
    }

    /**
     * Gets the key of this workspace repository. The key is used to distinguish one workspace from another and should
     * be sensitive to the artifacts that are (potentially) available in the workspace.
     * 
     * @return The (comparison) key for this workspace repository, never {@code null}.
     */
    public Object getKey()
    {
        return key;
    }

    @Override
    public String toString()
    {
        return "(" + getContentType() + ")";
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

        WorkspaceRepository that = (WorkspaceRepository) obj;

        return getContentType().equals( that.getContentType() ) && getKey().equals( that.getKey() );
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + getKey().hashCode();
        hash = hash * 31 + getContentType().hashCode();
        return hash;
    }

}
