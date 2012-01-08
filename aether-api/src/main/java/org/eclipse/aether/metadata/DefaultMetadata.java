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
package org.eclipse.aether.metadata;

import java.io.File;

/**
 * A basic metadata instance. <em>Note:</em> Instances of this class are immutable and the exposed mutators return new
 * objects rather than changing the current instance.
 */
public final class DefaultMetadata
    implements Metadata
{

    private final String groupId;

    private final String artifactId;

    private final String version;

    private final String type;

    private final Nature nature;

    private final File file;

    /**
     * Creates a new metadata for the repository root with the specific type and nature.
     * 
     * @param type The type of the metadata, e.g. "maven-metadata.xml", may be {@code null}.
     * @param nature The nature of the metadata, must not be {@code null}.
     */
    public DefaultMetadata( String type, Nature nature )
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

    /**
     * Creates a new metadata for the groupId level with the specific type and nature.
     * 
     * @param groupId The group identifier to which this metadata applies, may be {@code null}.
     * @param type The type of the metadata, e.g. "maven-metadata.xml", may be {@code null}.
     * @param nature The nature of the metadata, must not be {@code null}.
     */
    public DefaultMetadata( String groupId, String type, Nature nature )
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

    /**
     * Creates a new metadata for the groupId:artifactId level with the specific type and nature.
     * 
     * @param groupId The group identifier to which this metadata applies, may be {@code null}.
     * @param artifactId The artifact identifier to which this metadata applies, may be {@code null}.
     * @param type The type of the metadata, e.g. "maven-metadata.xml", may be {@code null}.
     * @param nature The nature of the metadata, must not be {@code null}.
     */
    public DefaultMetadata( String groupId, String artifactId, String type, Nature nature )
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

    /**
     * Creates a new metadata for the groupId:artifactId:version level with the specific type and nature.
     * 
     * @param groupId The group identifier to which this metadata applies, may be {@code null}.
     * @param artifactId The artifact identifier to which this metadata applies, may be {@code null}.
     * @param version The version to which this metadata applies, may be {@code null}.
     * @param type The type of the metadata, e.g. "maven-metadata.xml", may be {@code null}.
     * @param nature The nature of the metadata, must not be {@code null}.
     */
    public DefaultMetadata( String groupId, String artifactId, String version, String type, Nature nature )
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

    /**
     * Creates a new metadata for the groupId:artifactId:version level with the specific type and nature.
     * 
     * @param groupId The group identifier to which this metadata applies, may be {@code null}.
     * @param artifactId The artifact identifier to which this metadata applies, may be {@code null}.
     * @param version The version to which this metadata applies, may be {@code null}.
     * @param type The type of the metadata, e.g. "maven-metadata.xml", may be {@code null}.
     * @param nature The nature of the metadata, must not be {@code null}.
     * @param file The resolved file of the metadata, may be {@code null}.
     */
    public DefaultMetadata( String groupId, String artifactId, String version, String type, Nature nature, File file )
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
        return new DefaultMetadata( groupId, artifactId, version, type, nature, file );
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

}
