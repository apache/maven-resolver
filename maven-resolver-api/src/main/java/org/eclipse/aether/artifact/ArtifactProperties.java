/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.artifact;

import org.eclipse.aether.RepositorySystemSession;

/**
 * The keys for common properties of artifacts.
 *
 * @see Artifact#getProperties()
 */
public final class ArtifactProperties {

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
     *
     * @deprecated since 2.0, the semantic carried by this property and the fact this property is coupled to Resolver
     * 1.x "system" scope (that was delegated to consumer application) implies this property should not be used anymore,
     * instead, the {@link org.eclipse.aether.scope.ScopeManager} exposed via method
     * {@link RepositorySystemSession#getScopeManager()} should be used.
     */
    @Deprecated
    public static final String LOCAL_PATH = "localPath";

    /**
     * A boolean flag indicating whether the artifact presents some kind of bundle that physically includes its
     * dependencies, e.g. a fat WAR.
     *
     * @deprecated since 2.0, the semantic carried by this property should be defined in a custom
     *             {@link org.eclipse.aether.collection.DependencyTraverser} implementation provided by the resolver
     *             consumer
     */
    @Deprecated
    public static final String INCLUDES_DEPENDENCIES = "includesDependencies";

    /**
     * A boolean flag indicating whether the artifact is meant to be used for the compile/runtime/test build path of a
     * consumer project.
     * <p>
     * Note: This property is about "build path", whatever it means in the scope of the consumer project. It is NOT
     * about Java classpath or anything alike. How artifact is being consumed depends heavily on the consumer project.
     * Resolver is and will remain agnostic of consumer project use cases.
     *
     * @deprecated since 2.0, this property should be defined by the resolver consumer along with the {@link ArtifactType}
     *             implementation
     */
    @Deprecated
    public static final String CONSTITUTES_BUILD_PATH = "constitutesBuildPath";

    /**
     * The URL to a web page from which the artifact can be manually downloaded. This URL is not contacted by the
     * repository system but serves as a pointer for the end user to assist in getting artifacts that are not published
     * in a proper repository.
     */
    public static final String DOWNLOAD_URL = "downloadUrl";

    private ArtifactProperties() {
        // hide constructor
    }
}
