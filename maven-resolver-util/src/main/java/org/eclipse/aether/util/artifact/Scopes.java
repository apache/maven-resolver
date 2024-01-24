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
package org.eclipse.aether.util.artifact;

/**
 * The dependency scopes that Resolver supports out of the box. The full set of scopes should be defined by
 * consumer project.
 * <p>
 * By definition, scopes for Resolver are just labels, checked for equality. Resolver will not interpret, alter or
 * modify any scope "label" that is not present in this class. It is left to consumer projects to define and
 * interpret those.
 *
 * @see org.eclipse.aether.graph.Dependency#getScope()
 * @since 2.0.0
 */
public final class Scopes {

    /**
     * Special scope that tells resolver that dependency is not to be found in any regular repository, so it should not
     * even try to resolve the artifact from them. Dependency in this scope does not have artifact descriptor either.
     * Artifacts in this scope should have the "local path" property set, pointing to a file on local system, where the
     * backing file should reside. Resolution of artifacts in this scope fails, if backing file does not exist
     * (no property set, or property contains invalid path, or the path points to a non-existent file).
     *
     * @see org.eclipse.aether.artifact.ArtifactProperties#LOCAL_PATH
     */
    public static final String SYSTEM = "system";

    private Scopes() {
        // hide constructor
    }
}
