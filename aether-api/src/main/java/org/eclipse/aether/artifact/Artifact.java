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
import java.util.Map;

/**
 * A specific artifact. <em>Note:</em> Artifact instances are supposed to be immutable, e.g. any exposed mutator method
 * returns a new artifact instance and leaves the original instance unchanged. <em>Note:</em> Implementors are strongly
 * advised to inherit from {@link AbstractArtifact} instead of directly implementing this interface.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface Artifact
{

    /**
     * Gets the group identifier of this artifact, for example "org.apache.maven".
     * 
     * @return The group identifier, never {@code null}.
     */
    String getGroupId();

    /**
     * Gets the artifact identifier of this artifact, for example "maven-model".
     * 
     * @return The artifact identifier, never {@code null}.
     */
    String getArtifactId();

    /**
     * Gets the version of this artifact, for example "1.0-20100529-1213". Note that in case of meta versions like
     * "1.0-SNAPSHOT", the artifact's version depends on the state of the artifact. Artifacts that have been resolved or
     * deployed will usually have the meta version expanded.
     * 
     * @return The version, never {@code null}.
     */
    String getVersion();

    /**
     * Sets the version of the artifact.
     * 
     * @param version The version of this artifact, may be {@code null} or empty.
     * @return The new artifact, never {@code null}.
     */
    Artifact setVersion( String version );

    /**
     * Gets the base version of this artifact, for example "1.0-SNAPSHOT". In contrast to the {@link #getVersion()}, the
     * base version will always refer to the unresolved meta version.
     * 
     * @return The base version, never {@code null}.
     */
    String getBaseVersion();

    /**
     * Determines whether this artifact uses a snapshot version.
     * 
     * @return {@code true} if the artifact is a snapshot, {@code false} otherwise.
     */
    boolean isSnapshot();

    /**
     * Gets the classifier of this artifact, for example "sources".
     * 
     * @return The classifier or an empty string if none, never {@code null}.
     */
    String getClassifier();

    /**
     * Gets the (file) extension of this artifact, for example "jar".
     * 
     * @return The file extension, never {@code null}.
     */
    String getExtension();

    /**
     * Gets the file of this artifact. Note that only resolved artifacts have a file associated with them. In general,
     * callers must not assume any relationship between an artifact's filename and its coordinates.
     * 
     * @return The file or {@code null} if the artifact isn't resolved.
     */
    File getFile();

    /**
     * Sets the file of the artifact.
     * 
     * @param file The file of the artifact, may be {@code null}
     * @return The new artifact, never {@code null}.
     */
    Artifact setFile( File file );

    /**
     * Gets the specified property.
     * 
     * @param key The name of the property, must not be {@code null}.
     * @param defaultValue The default value to return in case the property is not set, may be {@code null}.
     * @see ArtifactProperties
     */
    String getProperty( String key, String defaultValue );

    /**
     * Gets the properties of this artifact.
     * 
     * @return The (read-only) properties, never {@code null}.
     * @see ArtifactProperties
     */
    Map<String, String> getProperties();

    /**
     * Sets the properties for the artifact.
     * 
     * @param properties The properties for the artifact, may be {@code null}.
     * @return The new artifact, never {@code null}.
     * @see ArtifactProperties
     */
    Artifact setProperties( Map<String, String> properties );

}
