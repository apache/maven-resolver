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

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

/**
 * Internal helper class for collector implementations.
 */
public final class DataPool {

    private static final String ARTIFACT_POOL = DataPool.class.getName() + "$Artifact";

    private static final String DEPENDENCY_POOL = DataPool.class.getName() + "$Dependency";

    private static final String DESCRIPTORS = DataPool.class.getName() + "$Descriptors";

    public static final ArtifactDescriptorResult NO_DESCRIPTOR =
            new ArtifactDescriptorResult(new ArtifactDescriptorRequest());

    private ObjectPool<Artifact> artifacts;

    private ObjectPool<Dependency> dependencies;

    private Map<Object, WeakReference<Descriptor>> descriptors;

    private final Map<Object, Constraint> constraints = new HashMap<>();

    private final Map<Object, List<DependencyNode>> nodes = new HashMap<>(256);

    @SuppressWarnings("unchecked")
    public DataPool(RepositorySystemSession session) {
        RepositoryCache cache = session.getCache();

        if (cache != null) {
            artifacts = (ObjectPool<Artifact>) cache.get(session, ARTIFACT_POOL);
            dependencies = (ObjectPool<Dependency>) cache.get(session, DEPENDENCY_POOL);
            descriptors = (Map<Object, WeakReference<Descriptor>>) cache.get(session, DESCRIPTORS);
        }

        if (artifacts == null) {
            artifacts = new ObjectPool<>();
            if (cache != null) {
                cache.put(session, ARTIFACT_POOL, artifacts);
            }
        }

        if (dependencies == null) {
            dependencies = new ObjectPool<>();
            if (cache != null) {
                cache.put(session, DEPENDENCY_POOL, dependencies);
            }
        }

        if (descriptors == null) {
            descriptors = Collections.synchronizedMap(new WeakHashMap<>(256));
            if (cache != null) {
                cache.put(session, DESCRIPTORS, descriptors);
            }
        }
    }

    public Artifact intern(Artifact artifact) {
        return artifacts.intern(artifact);
    }

    public Dependency intern(Dependency dependency) {
        return dependencies.intern(dependency);
    }

    public Object toKey(ArtifactDescriptorRequest request) {
        return request.getArtifact();
    }

    public ArtifactDescriptorResult getDescriptor(Object key, ArtifactDescriptorRequest request) {
        WeakReference<Descriptor> descriptorRef = descriptors.get(key);
        Descriptor descriptor = descriptorRef != null ? descriptorRef.get() : null;
        if (descriptor != null) {
            return descriptor.toResult(request);
        }
        return null;
    }

    public void putDescriptor(Object key, ArtifactDescriptorResult result) {
        descriptors.put(key, new WeakReference<>(new GoodDescriptor(result)));
    }

    public void putDescriptor(Object key, ArtifactDescriptorException e) {
        descriptors.put(key, new WeakReference<>(BadDescriptor.INSTANCE));
    }

    public Object toKey(VersionRangeRequest request) {
        return new ConstraintKey(request);
    }

    public VersionRangeResult getConstraint(Object key, VersionRangeRequest request) {
        Constraint constraint = constraints.get(key);
        if (constraint != null) {
            return constraint.toResult(request);
        }
        return null;
    }

    public void putConstraint(Object key, VersionRangeResult result) {
        constraints.put(key, new Constraint(result));
    }

    public Object toKey(
            Artifact artifact,
            List<RemoteRepository> repositories,
            DependencySelector selector,
            DependencyManager manager,
            DependencyTraverser traverser,
            VersionFilter filter) {
        return new GraphKey(artifact, repositories, selector, manager, traverser, filter);
    }

    public List<DependencyNode> getChildren(Object key) {
        return nodes.get(key);
    }

    public void putChildren(Object key, List<DependencyNode> children) {
        nodes.put(key, children);
    }

    abstract static class Descriptor {

        public abstract ArtifactDescriptorResult toResult(ArtifactDescriptorRequest request);
    }

    static final class GoodDescriptor extends Descriptor {

        final Artifact artifact;

        final List<Artifact> relocations;

        final Collection<Artifact> aliases;

        final List<RemoteRepository> repositories;

        final List<Dependency> dependencies;

        final List<Dependency> managedDependencies;

        GoodDescriptor(ArtifactDescriptorResult result) {
            artifact = result.getArtifact();
            relocations = result.getRelocations();
            aliases = result.getAliases();
            dependencies = result.getDependencies();
            managedDependencies = result.getManagedDependencies();
            repositories = result.getRepositories();
        }

        public ArtifactDescriptorResult toResult(ArtifactDescriptorRequest request) {
            ArtifactDescriptorResult result = new ArtifactDescriptorResult(request);
            result.setArtifact(artifact);
            result.setRelocations(relocations);
            result.setAliases(aliases);
            result.setDependencies(dependencies);
            result.setManagedDependencies(managedDependencies);
            result.setRepositories(repositories);
            return result;
        }
    }

    static final class BadDescriptor extends Descriptor {

        static final BadDescriptor INSTANCE = new BadDescriptor();

        public ArtifactDescriptorResult toResult(ArtifactDescriptorRequest request) {
            return NO_DESCRIPTOR;
        }
    }

    private static final class Constraint {
        final VersionRepo[] repositories;

        final VersionConstraint versionConstraint;

        Constraint(VersionRangeResult result) {
            versionConstraint = result.getVersionConstraint();
            List<Version> versions = result.getVersions();
            repositories = new VersionRepo[versions.size()];
            int i = 0;
            for (Version version : versions) {
                repositories[i++] = new VersionRepo(version, result.getRepository(version));
            }
        }

        VersionRangeResult toResult(VersionRangeRequest request) {
            VersionRangeResult result = new VersionRangeResult(request);
            for (VersionRepo vr : repositories) {
                result.addVersion(vr.version);
                result.setRepository(vr.version, vr.repo);
            }
            result.setVersionConstraint(versionConstraint);
            return result;
        }

        static final class VersionRepo {
            final Version version;

            final ArtifactRepository repo;

            VersionRepo(Version version, ArtifactRepository repo) {
                this.version = version;
                this.repo = repo;
            }
        }
    }

    static final class ConstraintKey {
        private final Artifact artifact;

        private final List<RemoteRepository> repositories;

        private final int hashCode;

        ConstraintKey(VersionRangeRequest request) {
            artifact = request.getArtifact();
            repositories = request.getRepositories();
            hashCode = artifact.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (!(obj instanceof ConstraintKey)) {
                return false;
            }
            ConstraintKey that = (ConstraintKey) obj;
            return artifact.equals(that.artifact) && equals(repositories, that.repositories);
        }

        private static boolean equals(List<RemoteRepository> repos1, List<RemoteRepository> repos2) {
            if (repos1.size() != repos2.size()) {
                return false;
            }
            for (Iterator<RemoteRepository> it1 = repos1.iterator(), it2 = repos2.iterator();
                    it1.hasNext() && it2.hasNext(); ) {
                RemoteRepository repo1 = it1.next();
                RemoteRepository repo2 = it2.next();
                if (repo1.isRepositoryManager() != repo2.isRepositoryManager()) {
                    return false;
                }
                if (repo1.isRepositoryManager()) {
                    if (!equals(repo1.getMirroredRepositories(), repo2.getMirroredRepositories())) {
                        return false;
                    }
                } else if (!repo1.getUrl().equals(repo2.getUrl())) {
                    return false;
                } else if (repo1.getPolicy(true).isEnabled()
                        != repo2.getPolicy(true).isEnabled()) {
                    return false;
                } else if (repo1.getPolicy(false).isEnabled()
                        != repo2.getPolicy(false).isEnabled()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    static final class GraphKey {
        private final Artifact artifact;

        private final List<RemoteRepository> repositories;

        private final DependencySelector selector;

        private final DependencyManager manager;

        private final DependencyTraverser traverser;

        private final VersionFilter filter;

        private final int hashCode;

        GraphKey(
                Artifact artifact,
                List<RemoteRepository> repositories,
                DependencySelector selector,
                DependencyManager manager,
                DependencyTraverser traverser,
                VersionFilter filter) {
            this.artifact = artifact;
            this.repositories = repositories;
            this.selector = selector;
            this.manager = manager;
            this.traverser = traverser;
            this.filter = filter;

            hashCode = Objects.hash(artifact, repositories, selector, manager, traverser, filter);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (!(obj instanceof GraphKey)) {
                return false;
            }
            GraphKey that = (GraphKey) obj;
            return Objects.equals(artifact, that.artifact)
                    && Objects.equals(repositories, that.repositories)
                    && Objects.equals(selector, that.selector)
                    && Objects.equals(manager, that.manager)
                    && Objects.equals(traverser, that.traverser)
                    && Objects.equals(filter, that.filter);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
