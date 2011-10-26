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
package org.eclipse.aether.artifact;

import java.io.File;
import java.util.Map;

/**
 * A specific artifact. <em>Note:</em> Artifact instances are supposed to be immutable, e.g. any exposed mutator method
 * returns a new artifact instance and leaves the original instance unchanged. Implementors are strongly advised to obey
 * this contract.
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
     * deployed will have the meta version expanded.
     * 
     * @return The version, never {@code null}.
     */
    String getVersion();

    /**
     * Sets the version of this artifact.
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
     * Gets the file of this artifact. Note that only resolved artifacts have a file associated with them.
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
     */
    String getProperty( String key, String defaultValue );

    /**
     * Gets the properties of this artifact. While the exact set of available properties is undefined, the following
     * properties are considered to be common:
     * <dl>
     * <dt>type</dt>
     * <dd>A high-level characterization of the artifact, e.g. "maven-plugin" or "test-jar".</dd>
     * <dt>language</dt>
     * <dd>The programming language this artifact is relevant for, e.g. "java" or "none".</dd>
     * <dt>includesDependencies</dt>
     * <dd>A boolean flag indicating whether the artifact presents some kind of bundle that physically includes its
     * dependencies, e.g. a fat WAR.</dd>
     * <dt>constitutesBuildPath</dt>
     * <dd>A boolean flag indicating whether the artifact is meant to be used for the compile/runtime/test build path of
     * a consumer project.</dd>
     * <dt>localPath</dt>
     * <dd>The (expected) path to the artifact on the local filesystem. An artifact which has this property set is
     * assumed to be not present in any regular repository and likewise has no artifact descriptor.</dd>
     * </dl>
     * 
     * @return The (read-only) properties, never {@code null}.
     */
    Map<String, String> getProperties();

    /**
     * Sets the properties for the artifact.
     * 
     * @param properties The properties for the artifact, may be {@code null}.
     * @return The new artifact, never {@code null}.
     */
    Artifact setProperties( Map<String, String> properties );

}
