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
package org.eclipse.aether.test.util.impl;

import java.io.File;

import org.eclipse.aether.metadata.Metadata;

/**
 */
public final class StubMetadata
    implements Metadata
{

    private final String groupId;

    private final String artifactId;

    private final String version;

    private final String type;

    private final Nature nature;

    private final File file;

    public StubMetadata( String type, Nature nature )
    {
        groupId = artifactId = version = "";
        this.type = ( type != null ) ? type : "";
        if ( nature == null )
        {
            throw new IllegalArgumentException( "metadata nature was not specified" );
        }
        this.nature = nature;
        this.file = null;
    }

    public StubMetadata( String groupId, String type, Nature nature )
    {
        this.groupId = ( groupId != null ) ? groupId : "";
        artifactId = version = "";
        this.type = ( type != null ) ? type : "";
        if ( nature == null )
        {
            throw new IllegalArgumentException( "metadata nature was not specified" );
        }
        this.nature = nature;
        this.file = null;
    }

    public StubMetadata( String groupId, String artifactId, String type, Nature nature )
    {
        this.groupId = ( groupId != null ) ? groupId : "";
        this.artifactId = ( artifactId != null ) ? artifactId : "";
        version = "";
        this.type = ( type != null ) ? type : "";
        if ( nature == null )
        {
            throw new IllegalArgumentException( "metadata nature was not specified" );
        }
        this.nature = nature;
        this.file = null;
    }

    public StubMetadata( String groupId, String artifactId, String version, String type, Nature nature )
    {
        this.groupId = ( groupId != null ) ? groupId : "";
        this.artifactId = ( artifactId != null ) ? artifactId : "";
        this.version = ( version != null ) ? version : "";
        this.type = ( type != null ) ? type : "";
        if ( nature == null )
        {
            throw new IllegalArgumentException( "metadata nature was not specified" );
        }
        this.nature = nature;
        this.file = null;
    }

    public StubMetadata( String groupId, String artifactId, String version, String type, Nature nature, File file )
    {
        this.groupId = ( groupId != null ) ? groupId : "";
        this.artifactId = ( artifactId != null ) ? artifactId : "";
        this.version = ( version != null ) ? version : "";
        this.type = ( type != null ) ? type : "";
        if ( nature == null )
        {
            throw new IllegalArgumentException( "metadata nature was not specified" );
        }
        this.nature = nature;
        this.file = file;
    }

    public String getGroupId()
    {
        return groupId;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public String getVersion()
    {
        return version;
    }

    public String getType()
    {
        return type;
    }

    public Nature getNature()
    {
        return nature;
    }

    public File getFile()
    {
        return file;
    }

    public Metadata setFile( File file )
    {
        return new StubMetadata( groupId, artifactId, version, type, nature, file );
    }

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder( 128 );
        if ( getGroupId().length() > 0 )
        {
            buffer.append( getGroupId() );
        }
        if ( getArtifactId().length() > 0 )
        {
            buffer.append( ':' ).append( getArtifactId() );
        }
        if ( getVersion().length() > 0 )
        {
            buffer.append( ':' ).append( getVersion() );
        }
        buffer.append( '/' ).append( getType() );
        return buffer.toString();
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( artifactId == null ) ? 0 : artifactId.hashCode() );
        result = prime * result + ( ( file == null ) ? 0 : file.hashCode() );
        result = prime * result + ( ( groupId == null ) ? 0 : groupId.hashCode() );
        result = prime * result + ( ( nature == null ) ? 0 : nature.hashCode() );
        result = prime * result + ( ( type == null ) ? 0 : type.hashCode() );
        result = prime * result + ( ( version == null ) ? 0 : version.hashCode() );
        return result;
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null )
        {
            return false;
        }
        if ( getClass() != obj.getClass() )
        {
            return false;
        }
        StubMetadata other = (StubMetadata) obj;
        if ( artifactId == null )
        {
            if ( other.artifactId != null )
            {
                return false;
            }
        }
        else if ( !artifactId.equals( other.artifactId ) )
        {
            return false;
        }
        if ( file == null )
        {
            if ( other.file != null )
            {
                return false;
            }
        }
        else if ( !file.equals( other.file ) )
        {
            return false;
        }
        if ( groupId == null )
        {
            if ( other.groupId != null )
            {
                return false;
            }
        }
        else if ( !groupId.equals( other.groupId ) )
        {
            return false;
        }
        if ( nature != other.nature )
        {
            return false;
        }
        if ( type == null )
        {
            if ( other.type != null )
            {
                return false;
            }
        }
        else if ( !type.equals( other.type ) )
        {
            return false;
        }
        if ( version == null )
        {
            if ( other.version != null )
            {
                return false;
            }
        }
        else if ( !version.equals( other.version ) )
        {
            return false;
        }
        return true;
    }

    public StubMetadata setVersion( String version )
    {
        return new StubMetadata( groupId, artifactId, version, type, nature, file );
    }

}
