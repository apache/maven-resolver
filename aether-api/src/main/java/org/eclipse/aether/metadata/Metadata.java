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
import java.util.Map;

/**
 * A piece of repository metadata, e.g. an index of available versions. In contrast to an artifact, which usually exists
 * in only one repository, metadata usually exists in multiple repositories and each repository contains a different
 * copy of the metadata. <em>Note:</em> Metadata instances are supposed to be immutable, e.g. any exposed mutator method
 * returns a new metadata instance and leaves the original instance unchanged. Implementors are strongly advised to obey
 * this contract. <em>Note:</em> Implementors are strongly advised to inherit from {@link AbstractMetadata} instead of
 * directly implementing this interface.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface Metadata
{

    /**
     * The nature of the metadata.
     */
    enum Nature
    {
        /**
         * The metadata refers to release artifacts only.
         */
        RELEASE,

        /**
         * The metadata refers to snapshot artifacts only.
         */
        SNAPSHOT,

        /**
         * The metadata refers to either release or snapshot artifacts.
         */
        RELEASE_OR_SNAPSHOT
    }

    /**
     * Gets the group identifier of this metadata.
     * 
     * @return The group identifier or an empty string if the metadata applies to the entire repository, never
     *         {@code null}.
     */
    String getGroupId();

    /**
     * Gets the artifact identifier of this metadata.
     * 
     * @return The artifact identifier or an empty string if the metadata applies to the groupId level only, never
     *         {@code null}.
     */
    String getArtifactId();

    /**
     * Gets the version of this metadata.
     * 
     * @return The version or an empty string if the metadata applies to the groupId:artifactId level only, never
     *         {@code null}.
     */
    String getVersion();

    /**
     * Gets the type of the metadata, e.g. "maven-metadata.xml".
     * 
     * @return The type of the metadata, never {@code null}.
     */
    String getType();

    /**
     * Gets the nature of this metadata. The nature indicates to what artifact versions the metadata refers.
     * 
     * @return The nature, never {@code null}.
     */
    Nature getNature();

    /**
     * Gets the file of this metadata. Note that only resolved metadata has a file associated with it.
     * 
     * @return The file or {@code null} if none.
     */
    File getFile();

    /**
     * Sets the file of the metadata.
     * 
     * @param file The file of the metadata, may be {@code null}
     * @return The new metadata, never {@code null}.
     */
    Metadata setFile( File file );

    /**
     * Gets the specified property.
     * 
     * @param key The name of the property, must not be {@code null}.
     * @param defaultValue The default value to return in case the property is not set, may be {@code null}.
     * @return The requested property value or {@code null} if the property is not set and no default value was
     *         provided.
     */
    String getProperty( String key, String defaultValue );

    /**
     * Gets the properties of this metadata.
     * 
     * @return The (read-only) properties, never {@code null}.
     */
    Map<String, String> getProperties();

    /**
     * Sets the properties for the metadata.
     * 
     * @param properties The properties for the metadata, may be {@code null}.
     * @return The new metadata, never {@code null}.
     */
    Metadata setProperties( Map<String, String> properties );

}
