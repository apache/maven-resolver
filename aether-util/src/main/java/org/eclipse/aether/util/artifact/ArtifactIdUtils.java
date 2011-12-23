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
package org.eclipse.aether.util.artifact;

import org.eclipse.aether.artifact.Artifact;

/**
 * A utility class to create identifiers for artifacts.
 */
public final class ArtifactIdUtils
{

    private static final char SEP = ':';

    private ArtifactIdUtils()
    {
        // hide constructor
    }

    /**
     * Creates an artifact identifier of the form {@code <groupId>:<artifactId>:<extension>[:<classifier>]:<version>}.
     * 
     * @param artifact The artifact to create an identifer for, may be {@code null}.
     * @return The artifact identifier or {@code null} if the input was {@code null}.
     */
    public static String toId( Artifact artifact )
    {
        String id = null;
        if ( artifact != null )
        {
            id =
                toId( artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                      artifact.getClassifier(), artifact.getVersion() );
        }
        return id;
    }

    /**
     * Creates an artifact identifier of the form {@code <groupId>:<artifactId>:<extension>[:<classifier>]:<version>}.
     * 
     * @param groupId The group id, may be {@code null}.
     * @param artifactId The artifact id, may be {@code null}.
     * @param extension The file extensiion, may be {@code null}.
     * @param classifier The classifier, may be {@code null}.
     * @param version The version, may be {@code null}.
     * @return The artifact identifier, never {@code null}.
     */
    public static String toId( String groupId, String artifactId, String extension, String classifier, String version )
    {
        StringBuilder buffer = concat( groupId, artifactId, extension, classifier );
        buffer.append( SEP );
        if ( version != null )
        {
            buffer.append( version );
        }
        return buffer.toString();
    }

    /**
     * Creates an artifact identifier of the form
     * {@code <groupId>:<artifactId>:<extension>[:<classifier>]:<baseVersion>}.
     * 
     * @param artifact The artifact to create an identifer for, may be {@code null}.
     * @return The artifact identifier or {@code null} if the input was {@code null}.
     */
    public static String toBaseId( Artifact artifact )
    {
        String id = null;
        if ( artifact != null )
        {
            id =
                toId( artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                      artifact.getClassifier(), artifact.getBaseVersion() );
        }
        return id;
    }

    /**
     * Creates an artifact identifier of the form {@code <groupId>:<artifactId>:<extension>[:<classifier>]}.
     * 
     * @param artifact The artifact to create an identifer for, may be {@code null}.
     * @return The artifact identifier or {@code null} if the input was {@code null}.
     */
    public static String toVersionlessId( Artifact artifact )
    {
        String id = null;
        if ( artifact != null )
        {
            id =
                toVersionlessId( artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                                 artifact.getClassifier() );
        }
        return id;
    }

    /**
     * Creates an artifact identifier of the form {@code <groupId>:<artifactId>:<extension>[:<classifier>]}.
     * 
     * @param groupId The group id, may be {@code null}.
     * @param artifactId The artifact id, may be {@code null}.
     * @param extension The file extensiion, may be {@code null}.
     * @param classifier The classifier, may be {@code null}.
     * @return The artifact identifier, never {@code null}.
     */
    public static String toVersionlessId( String groupId, String artifactId, String extension, String classifier )
    {
        return concat( groupId, artifactId, extension, classifier ).toString();
    }

    private static StringBuilder concat( String groupId, String artifactId, String extension, String classifier )
    {
        StringBuilder buffer = new StringBuilder( 128 );

        if ( groupId != null )
        {
            buffer.append( groupId );
        }
        buffer.append( SEP );
        if ( artifactId != null )
        {
            buffer.append( artifactId );
        }
        buffer.append( SEP );
        if ( extension != null )
        {
            buffer.append( extension );
        }
        if ( classifier != null && classifier.length() > 0 )
        {
            buffer.append( SEP ).append( classifier );
        }

        return buffer;
    }

}
