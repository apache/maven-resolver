/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.artifact;

import java.util.Map;

/**
 * An artifact type describing artifact characteristics that are common for certain artifacts. Artifact types are a
 * means to simplify the description of an artifact by referring to an artifact type instead of specifying the various
 * properties individually.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface ArtifactType
{

    /**
     * Gets the identifier of this type, e.g. "maven-plugin" or "test-jar".
     * 
     * @return The identifier of this type, never {@code null}.
     * @see ArtifactProperties#TYPE
     */
    String getId();

    /**
     * Gets the file extension to use for artifacts of this type (unless explicitly overridden by the artifact).
     * 
     * @return The usual file extension, never {@code null}.
     */
    String getExtension();

    /**
     * Gets the classifier to use for artifacts of this type (unless explicitly overridden by the artifact).
     * 
     * @return The usual classifier or an empty string if none, never {@code null}.
     */
    String getClassifier();

    /**
     * Gets the properties to use for artifacts of this type (unless explicitly overridden by the artifact).
     * 
     * @return The (read-only) properties, never {@code null}.
     * @see ArtifactProperties
     */
    Map<String, String> getProperties();

}
