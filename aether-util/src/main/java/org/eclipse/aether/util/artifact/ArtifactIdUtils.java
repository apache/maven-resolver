/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.util.artifact;

import org.eclipse.aether.artifact.Artifact;

/**
 * A utility class for artifact identifiers.
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

    /**
     * Determines whether two artifacts have the same identifier. This method is equivalent to calling
     * {@link String#equals(Object)} on the return values from {@link #toId(Artifact)} for the artifacts but does not
     * incur the overhead of creating temporary strings.
     * 
     * @param artifact1 The first artifact, may be {@code null}.
     * @param artifact2 The second artifact, may be {@code null}.
     * @return {@code true} if both artifacts are not {@code null} and have equal ids, {@code false} otherwise.
     */
    public static boolean equalsId( Artifact artifact1, Artifact artifact2 )
    {
        if ( artifact1 == null || artifact2 == null )
        {
            return false;
        }
        if ( !eq( artifact1.getArtifactId(), artifact2.getArtifactId() ) )
        {
            return false;
        }
        if ( !eq( artifact1.getGroupId(), artifact2.getGroupId() ) )
        {
            return false;
        }
        if ( !eq( artifact1.getExtension(), artifact2.getExtension() ) )
        {
            return false;
        }
        if ( !eq( artifact1.getClassifier(), artifact2.getClassifier() ) )
        {
            return false;
        }
        if ( !eq( artifact1.getVersion(), artifact2.getVersion() ) )
        {
            return false;
        }
        return true;
    }

    /**
     * Determines whether two artifacts have the same base identifier. This method is equivalent to calling
     * {@link String#equals(Object)} on the return values from {@link #toBaseId(Artifact)} for the artifacts but does
     * not incur the overhead of creating temporary strings.
     * 
     * @param artifact1 The first artifact, may be {@code null}.
     * @param artifact2 The second artifact, may be {@code null}.
     * @return {@code true} if both artifacts are not {@code null} and have equal base ids, {@code false} otherwise.
     */
    public static boolean equalsBaseId( Artifact artifact1, Artifact artifact2 )
    {
        if ( artifact1 == null || artifact2 == null )
        {
            return false;
        }
        if ( !eq( artifact1.getArtifactId(), artifact2.getArtifactId() ) )
        {
            return false;
        }
        if ( !eq( artifact1.getGroupId(), artifact2.getGroupId() ) )
        {
            return false;
        }
        if ( !eq( artifact1.getExtension(), artifact2.getExtension() ) )
        {
            return false;
        }
        if ( !eq( artifact1.getClassifier(), artifact2.getClassifier() ) )
        {
            return false;
        }
        if ( !eq( artifact1.getBaseVersion(), artifact2.getBaseVersion() ) )
        {
            return false;
        }
        return true;
    }

    /**
     * Determines whether two artifacts have the same versionless identifier. This method is equivalent to calling
     * {@link String#equals(Object)} on the return values from {@link #toVersionlessId(Artifact)} for the artifacts but
     * does not incur the overhead of creating temporary strings.
     * 
     * @param artifact1 The first artifact, may be {@code null}.
     * @param artifact2 The second artifact, may be {@code null}.
     * @return {@code true} if both artifacts are not {@code null} and have equal versionless ids, {@code false}
     *         otherwise.
     */
    public static boolean equalsVersionlessId( Artifact artifact1, Artifact artifact2 )
    {
        if ( artifact1 == null || artifact2 == null )
        {
            return false;
        }
        if ( !eq( artifact1.getArtifactId(), artifact2.getArtifactId() ) )
        {
            return false;
        }
        if ( !eq( artifact1.getGroupId(), artifact2.getGroupId() ) )
        {
            return false;
        }
        if ( !eq( artifact1.getExtension(), artifact2.getExtension() ) )
        {
            return false;
        }
        if ( !eq( artifact1.getClassifier(), artifact2.getClassifier() ) )
        {
            return false;
        }
        return true;
    }

    private static <T> boolean eq( T s1, T s2 )
    {
        return s1 != null ? s1.equals( s2 ) : s2 == null;
    }

}
