package org.eclipse.aether.artifact;

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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static java.util.Objects.requireNonNull;

/**
 * A simple artifact type.
 */
public final class DefaultArtifactType
    implements ArtifactType
{

    private final String id;

    private final String extension;

    private final String classifier;

    private final Map<String, String> properties;

    /**
     * Creates a new artifact type with the specified identifier. This constructor assumes the usual file extension
     * equals the given type id and that the usual classifier is empty. Additionally, the properties
     * {@link ArtifactProperties#LANGUAGE}, {@link ArtifactProperties#CONSTITUTES_BUILD_PATH} and
     * {@link ArtifactProperties#INCLUDES_DEPENDENCIES} will be set to {@code "none"}, {@code true} and {@code false},
     * respectively.
     * 
     * @param id The identifier of the type which will also be used as the value for the {@link ArtifactProperties#TYPE}
     *            property, must not be {@code null} or empty.
     */
    public DefaultArtifactType( String id )
    {
        this( id, id, "", "none", false, false );
    }

    /**
     * Creates a new artifact type with the specified properties. Additionally, the properties
     * {@link ArtifactProperties#CONSTITUTES_BUILD_PATH} and {@link ArtifactProperties#INCLUDES_DEPENDENCIES} will be
     * set to {@code true} and {@code false}, respectively.
     * 
     * @param id The identifier of the type which will also be used as the value for the {@link ArtifactProperties#TYPE}
     *            property, must not be {@code null} or empty.
     * @param extension The usual file extension for artifacts of this type, may be {@code null}.
     * @param classifier The usual classifier for artifacts of this type, may be {@code null}.
     * @param language The value for the {@link ArtifactProperties#LANGUAGE} property, may be {@code null}.
     */
    public DefaultArtifactType( String id, String extension, String classifier, String language )
    {
        this( id, extension, classifier, language, true, false );
    }

    /**
     * Creates a new artifact type with the specified properties.
     * 
     * @param id The identifier of the type which will also be used as the value for the {@link ArtifactProperties#TYPE}
     *            property, must not be {@code null} or empty.
     * @param extension The usual file extension for artifacts of this type, may be {@code null}.
     * @param classifier The usual classifier for artifacts of this type, may be {@code null}.
     * @param language The value for the {@link ArtifactProperties#LANGUAGE} property, may be {@code null}.
     * @param constitutesBuildPath The value for the {@link ArtifactProperties#CONSTITUTES_BUILD_PATH} property.
     * @param includesDependencies The value for the {@link ArtifactProperties#INCLUDES_DEPENDENCIES} property.
     */
    public DefaultArtifactType( String id, String extension, String classifier, String language,
                                boolean constitutesBuildPath, boolean includesDependencies )
    {
        this.id = requireNonNull( id, "type id cannot be null" );
        if ( id.length() == 0 )
        {
            throw new IllegalArgumentException( "type id cannot be empty" );
        }
        this.extension = emptify( extension );
        this.classifier = emptify( classifier );
        Map<String, String> props = new HashMap<>();
        props.put( ArtifactProperties.TYPE, id );
        props.put( ArtifactProperties.LANGUAGE, ( language != null && language.length() > 0 ) ? language : "none" );
        props.put( ArtifactProperties.INCLUDES_DEPENDENCIES, Boolean.toString( includesDependencies ) );
        props.put( ArtifactProperties.CONSTITUTES_BUILD_PATH, Boolean.toString( constitutesBuildPath ) );
        properties = Collections.unmodifiableMap( props );
    }

    /**
     * Creates a new artifact type with the specified properties.
     * 
     * @param id The identifier of the type, must not be {@code null} or empty.
     * @param extension The usual file extension for artifacts of this type, may be {@code null}.
     * @param classifier The usual classifier for artifacts of this type, may be {@code null}.
     * @param properties The properties for artifacts of this type, may be {@code null}.
     */
    public DefaultArtifactType( String id, String extension, String classifier, Map<String, String> properties )
    {
        this.id = requireNonNull( id, "type id cannot be null" );
        if ( id.length() == 0 )
        {
            throw new IllegalArgumentException( "type id cannot be empty" );
        }
        this.extension = emptify( extension );
        this.classifier = emptify( classifier );
        this.properties = AbstractArtifact.copyProperties( properties );
    }

    private static String emptify( String str )
    {
        return ( str == null ) ? "" : str;
    }

    public String getId()
    {
        return id;
    }

    public String getExtension()
    {
        return extension;
    }

    public String getClassifier()
    {
        return classifier;
    }

    public Map<String, String> getProperties()
    {
        return properties;
    }

}
