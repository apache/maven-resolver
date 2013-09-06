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

/**
 * The keys for common properties of artifacts.
 * 
 * @see Artifact#getProperties()
 */
public final class ArtifactProperties
{

    /**
     * A high-level characterization of the artifact, e.g. "maven-plugin" or "test-jar".
     * 
     * @see ArtifactType#getId()
     */
    public static final String TYPE = "type";

    /**
     * The programming language this artifact is relevant for, e.g. "java" or "none".
     */
    public static final String LANGUAGE = "language";

    /**
     * The (expected) path to the artifact on the local filesystem. An artifact which has this property set is assumed
     * to be not present in any regular repository and likewise has no artifact descriptor. Artifact resolution will
     * verify the path and resolve the artifact if the path actually denotes an existing file. If the path isn't valid,
     * resolution will fail and no attempts to search local/remote repositories are made.
     */
    public static final String LOCAL_PATH = "localPath";

    /**
     * A boolean flag indicating whether the artifact presents some kind of bundle that physically includes its
     * dependencies, e.g. a fat WAR.
     */
    public static final String INCLUDES_DEPENDENCIES = "includesDependencies";

    /**
     * A boolean flag indicating whether the artifact is meant to be used for the compile/runtime/test build path of a
     * consumer project.
     */
    public static final String CONSTITUTES_BUILD_PATH = "constitutesBuildPath";

    /**
     * The URL to a web page from which the artifact can be manually downloaded. This URL is not contacted by the
     * repository system but serves as a pointer for the end user to assist in getting artifacts that are not published
     * in a proper repository.
     */
    public static final String DOWNLOAD_URL = "downloadUrl";

    private ArtifactProperties()
    {
        // hide constructor
    }

}
