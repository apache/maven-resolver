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
package org.eclipse.aether.internal.impl.collect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

/**
 * Default implementation of {@link VersionFilter.VersionFilterContext}.
 * Internal helper class for collector implementations.
 * This instance is not thread safe, and same context instance must not be shared across threads.
 */
public final class DefaultVersionFilterContext implements VersionFilter.VersionFilterContext {
    private final RepositorySystemSession session;

    private Dependency dependency;

    private VersionRangeResult result;

    private ArrayList<Version> versions;

    public DefaultVersionFilterContext(RepositorySystemSession session) {
        this.session = session;
        this.dependency = null;
        this.result = null;
        this.versions = null;
    }

    private DefaultVersionFilterContext(
            RepositorySystemSession session, Dependency dependency, VersionRangeResult result) {
        this.session = session;
        this.dependency = dependency;
        this.result = result;
        this.versions = new ArrayList<>(result.getVersions());
    }

    /**
     * The use of this method allows strictly single-threaded use of context. Is unused in production, only in tests.
     */
    @Deprecated
    public void set(Dependency dependency, VersionRangeResult result) {
        this.dependency = dependency;
        this.result = result;
        this.versions = new ArrayList<>(result.getVersions());
    }

    /**
     * Creates an initialized, new context instance out of this context session and provided dependency and result.
     * The newly created context is still not thread safe, but allows to have multiple contexts with different
     * dependencies and results in the same time (processed in multiple threads in parallel like BF collector does).
     */
    public DefaultVersionFilterContext initialize(Dependency dependency, VersionRangeResult result) {
        return new DefaultVersionFilterContext(this.session, dependency, result);
    }

    public List<Version> get() {
        return new ArrayList<>(versions);
    }

    @Override
    public RepositorySystemSession getSession() {
        return session;
    }

    @Override
    public Dependency getDependency() {
        return dependency;
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return result.getVersionConstraint();
    }

    @Override
    public int getCount() {
        return versions.size();
    }

    @Override
    public ArtifactRepository getRepository(Version version) {
        return result.getRepository(version);
    }

    @Override
    public List<RemoteRepository> getRepositories() {
        return Collections.unmodifiableList(result.getRequest().getRepositories());
    }

    @Override
    public Iterator<Version> iterator() {
        return versions.iterator();
    }

    @Override
    public String toString() {
        return dependency + " " + result.getVersions();
    }
}
