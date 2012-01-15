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
package org.eclipse.aether.artifact;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * A simple artifact. <em>Note:</em> Instances of this class are immutable and the exposed mutators return new objects
 * rather than changing the current instance.
 */
public final class DefaultArtifact
    extends AbstractArtifact
{

    private final String groupId;

    private final String artifactId;

    private final String version;

    private final String classifier;

    private final String extension;

    private final File file;

    private final Map<String, String> properties;

    /**
     * Creates a new artifact with the specified coordinates. If not specified in the artifact coordinates, the
     * artifact's extension defaults to {@code jar} and classifier to an empty string.
     * 
     * @param coords The artifact coordinates in the format
     *            {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}, must not be {@code null}.
     */
    public DefaultArtifact( String coords )
    {
        this( coords, Collections.<String, String> emptyMap() );
    }

    /**
     * Creates a new artifact with the specified coordinates and properties. If not specified in the artifact
     * coordinates, the artifact's extension defaults to {@code jar} and classifier to an empty string.
     * 
     * @param coords The artifact coordinates in the format
     *            {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}, must not be {@code null}.
     * @param properties The artifact properties, may be {@code null}.
     */
    public DefaultArtifact( String coords, Map<String, String> properties )
    {
        Pattern p = Pattern.compile( "([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)" );
        Matcher m = p.matcher( coords );
        if ( !m.matches() )
        {
            throw new IllegalArgumentException( "Bad artifact coordinates " + coords
                + ", expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>" );
        }
        groupId = m.group( 1 );
        artifactId = m.group( 2 );
        extension = get( m.group( 4 ), "jar" );
        classifier = get( m.group( 6 ), "" );
        version = m.group( 7 );
        file = null;
        this.properties = copyProperties( properties );
    }

    private static String get( String value, String defaultValue )
    {
        return ( value == null || value.length() <= 0 ) ? defaultValue : value;
    }

    /**
     * Creates a new artifact with the specified coordinates and no classifier. Passing {@code null} for any of the
     * coordinates is equivalent to specifying an empty string.
     * 
     * @param groupId The group identifier of the artifact, may be {@code null}.
     * @param artifactId The artifact identifier of the artifact, may be {@code null}.
     * @param extension The file extension of the artifact, may be {@code null}.
     * @param version The version of the artifact, may be {@code null}.
     */
    public DefaultArtifact( String groupId, String artifactId, String extension, String version )
    {
        this( groupId, artifactId, "", extension, version );
    }

    /**
     * Creates a new artifact with the specified coordinates. Passing {@code null} for any of the coordinates is
     * equivalent to specifying an empty string.
     * 
     * @param groupId The group identifier of the artifact, may be {@code null}.
     * @param artifactId The artifact identifier of the artifact, may be {@code null}.
     * @param classifier The classifier of the artifact, may be {@code null}.
     * @param extension The file extension of the artifact, may be {@code null}.
     * @param version The version of the artifact, may be {@code null}.
     */
    public DefaultArtifact( String groupId, String artifactId, String classifier, String extension, String version )
    {
        this( groupId, artifactId, classifier, extension, version, null, (File) null );
    }

    /**
     * Creates a new artifact with the specified coordinates. Passing {@code null} for any of the coordinates is
     * equivalent to specifying an empty string. The optional artifact type provided to this constructor will be used to
     * determine the artifact's classifier and file extension if the corresponding arguments for this constructor are
     * {@code null}.
     * 
     * @param groupId The group identifier of the artifact, may be {@code null}.
     * @param artifactId The artifact identifier of the artifact, may be {@code null}.
     * @param classifier The classifier of the artifact, may be {@code null}.
     * @param extension The file extension of the artifact, may be {@code null}.
     * @param version The version of the artifact, may be {@code null}.
     * @param type The artifact type from which to query classifier, file extension and properties, may be {@code null}.
     */
    public DefaultArtifact( String groupId, String artifactId, String classifier, String extension, String version,
                            ArtifactType type )
    {
        this( groupId, artifactId, classifier, extension, version, null, type );
    }

    /**
     * Creates a new artifact with the specified coordinates and properties. Passing {@code null} for any of the
     * coordinates is equivalent to specifying an empty string. The optional artifact type provided to this constructor
     * will be used to determine the artifact's classifier and file extension if the corresponding arguments for this
     * constructor are {@code null}. If the artifact type specifies properties, those will get merged with the
     * properties passed directly into the constructor, with the latter properties taking precedence.
     * 
     * @param groupId The group identifier of the artifact, may be {@code null}.
     * @param artifactId The artifact identifier of the artifact, may be {@code null}.
     * @param classifier The classifier of the artifact, may be {@code null}.
     * @param extension The file extension of the artifact, may be {@code null}.
     * @param version The version of the artifact, may be {@code null}.
     * @param properties The properties of the artifact, may be {@code null} if none.
     * @param type The artifact type from which to query classifier, file extension and properties, may be {@code null}.
     */
    public DefaultArtifact( String groupId, String artifactId, String classifier, String extension, String version,
                            Map<String, String> properties, ArtifactType type )
    {
        this.groupId = emptify( groupId );
        this.artifactId = emptify( artifactId );
        if ( classifier != null || type == null )
        {
            this.classifier = emptify( classifier );
        }
        else
        {
            this.classifier = emptify( type.getClassifier() );
        }
        if ( extension != null || type == null )
        {
            this.extension = emptify( extension );
        }
        else
        {
            this.extension = emptify( type.getExtension() );
        }
        this.version = emptify( version );
        this.file = null;
        this.properties = merge( properties, ( type != null ) ? type.getProperties() : null );
    }

    private static Map<String, String> merge( Map<String, String> dominant, Map<String, String> recessive )
    {
        Map<String, String> properties;

        if ( ( dominant == null || dominant.isEmpty() ) && ( recessive == null || recessive.isEmpty() ) )
        {
            properties = Collections.emptyMap();
        }
        else
        {
            properties = new HashMap<String, String>();
            if ( recessive != null )
            {
                properties.putAll( recessive );
            }
            if ( dominant != null )
            {
                properties.putAll( dominant );
            }
            properties = Collections.unmodifiableMap( properties );
        }

        return properties;
    }

    /**
     * Creates a new artifact with the specified coordinates, properties and file. Passing {@code null} for any of the
     * coordinates is equivalent to specifying an empty string.
     * 
     * @param groupId The group identifier of the artifact, may be {@code null}.
     * @param artifactId The artifact identifier of the artifact, may be {@code null}.
     * @param classifier The classifier of the artifact, may be {@code null}.
     * @param extension The file extension of the artifact, may be {@code null}.
     * @param version The version of the artifact, may be {@code null}.
     * @param properties The properties of the artifact, may be {@code null} if none.
     * @param file The resolved file of the artifact, may be {@code null}.
     */
    public DefaultArtifact( String groupId, String artifactId, String classifier, String extension, String version,
                            Map<String, String> properties, File file )
    {
        this.groupId = emptify( groupId );
        this.artifactId = emptify( artifactId );
        this.classifier = emptify( classifier );
        this.extension = emptify( extension );
        this.version = emptify( version );
        this.file = file;
        this.properties = copyProperties( properties );
    }

    DefaultArtifact( String groupId, String artifactId, String classifier, String extension, String version, File file,
                     Map<String, String> properties )
    {
        // NOTE: This constructor assumes immutability of the provided properties, for internal use only
        this.groupId = emptify( groupId );
        this.artifactId = emptify( artifactId );
        this.classifier = emptify( classifier );
        this.extension = emptify( extension );
        this.version = emptify( version );
        this.file = file;
        this.properties = properties;
    }

    private static String emptify( String str )
    {
        return ( str == null ) ? "" : str;
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

    public String getClassifier()
    {
        return classifier;
    }

    public String getExtension()
    {
        return extension;
    }

    public File getFile()
    {
        return file;
    }

    public Map<String, String> getProperties()
    {
        return properties;
    }

}
