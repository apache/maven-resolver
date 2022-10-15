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
package org.eclipse.aether.internal.test.util;

import java.util.List;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.VersionRangeResult;

/**
 * Utility methods to help unit testing.
 */
public class TestUtils {

    private TestUtils() {
        // hide constructor
    }

    /**
     * Creates a new repository session whose local repository manager is initialized with an instance of
     * {@link TestLocalRepositoryManager}.
     */
    public static DefaultRepositorySystemSession newSession() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        session.setLocalRepositoryManager(new TestLocalRepositoryManager());
        return session;
    }

    /**
     * Creates a new dependency collection context.
     */
    public static DependencyCollectionContext newCollectionContext(
            RepositorySystemSession session, Dependency dependency, List<Dependency> managedDependencies) {
        return new TestDependencyCollectionContext(session, null, dependency, managedDependencies);
    }

    /**
     * Creates a new dependency collection context.
     */
    public static DependencyCollectionContext newCollectionContext(
            RepositorySystemSession session,
            Artifact artifact,
            Dependency dependency,
            List<Dependency> managedDependencies) {
        return new TestDependencyCollectionContext(session, artifact, dependency, managedDependencies);
    }

    /**
     * Creates a new dependency graph transformation context.
     */
    public static DependencyGraphTransformationContext newTransformationContext(RepositorySystemSession session) {
        return new TestDependencyGraphTransformationContext(session);
    }

    /**
     * Creates a new version filter context from the specified session and version range result.
     */
    public static VersionFilter.VersionFilterContext newVersionFilterContext(
            RepositorySystemSession session, VersionRangeResult rangeResult) {
        return new TestVersionFilterContext(session, rangeResult);
    }
}
