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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

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
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

/**
 * Internal helper class for collector implementations.
 */
public final class DataPool {
    public static final String CONFIG_PROPS_PREFIX = DefaultDependencyCollector.CONFIG_PROPS_PREFIX + "pool.";

    /**
     * Flag controlling interning data pool type used by dependency collector for Artifact instances, matters for
     * heap consumption. By default, uses “weak” references (consume less heap). Using “hard” will make it much
     * more memory aggressive and possibly faster (system and Java dependent). Supported values: "hard", "weak".
     *
     * @since 1.9.5
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #WEAK}
     */
    public static final String CONFIG_PROP_COLLECTOR_POOL_ARTIFACT = CONFIG_PROPS_PREFIX + "artifact";

    /**
     * Flag controlling interning data pool type used by dependency collector for Dependency instances, matters for
     * heap consumption. By default, uses “weak” references (consume less heap). Using “hard” will make it much
     * more memory aggressive and possibly faster (system and Java dependent). Supported values: "hard", "weak".
     *
     * @since 1.9.5
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #WEAK}
     */
    public static final String CONFIG_PROP_COLLECTOR_POOL_DEPENDENCY = CONFIG_PROPS_PREFIX + "dependency";

    /**
     * Flag controlling interning data pool type used by dependency collector for ArtifactDescriptor (POM) instances,
     * matters for heap consumption. By default, uses “weak” references (consume less heap). Using “hard” will make it
     * much more memory aggressive and possibly faster (system and Java dependent). Supported values: "hard", "weak".
     *
     * @since 1.9.5
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #HARD}
     */
    public static final String CONFIG_PROP_COLLECTOR_POOL_DESCRIPTOR = CONFIG_PROPS_PREFIX + "descriptor";

    /**
     * Flag controlling interning data pool type used by dependency lists collector for ArtifactDescriptor (POM) instances,
     * matters for heap consumption. By default, uses “weak” references (consume less heap). Using “hard” will make it
     * much more memory aggressive and possibly faster (system and Java dependent). Supported values: "hard", "weak".
     *
     * @since 1.9.22
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #HARD}
     */
    public static final String CONFIG_PROP_COLLECTOR_POOL_DEPENDENCY_LISTS =
            "aether.dependencyCollector.pool.dependencyLists";

    /**
     * Flag controlling interning artifact descriptor dependencies.
     *
     * @since 1.9.22
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue false
     */
    public static final String CONFIG_PROP_COLLECTOR_POOL_INTERN_ARTIFACT_DESCRIPTOR_DEPENDENCIES =
            "aether.dependencyCollector.pool.internArtifactDescriptorDependencies";

    /**
     * Flag controlling interning artifact descriptor managed dependencies.
     *
     * @since 1.9.22
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue true
     */
    public static final String CONFIG_PROP_COLLECTOR_POOL_INTERN_ARTIFACT_DESCRIPTOR_MANAGED_DEPENDENCIES =
            "aether.dependencyCollector.pool.internArtifactDescriptorManagedDependencies";

    private static final String ARTIFACT_POOL = DataPool.class.getName() + "$Artifact";

    private static final String DEPENDENCY_POOL = DataPool.class.getName() + "$Dependency";

    private static final String DESCRIPTORS = DataPool.class.getName() + "$Descriptors";

    private static final String DEPENDENCY_LISTS_POOL = DataPool.class.getName() + "$DependencyLists";

    public static final ArtifactDescriptorResult NO_DESCRIPTOR =
            new ArtifactDescriptorResult(new ArtifactDescriptorRequest());

    /**
     * Artifact interning pool, lives across session (if session carries non-null {@link RepositoryCache}).
     */
    private final InternPool<Artifact, Artifact> artifacts;

    /**
     * Dependency interning pool, lives across session (if session carries non-null {@link RepositoryCache}).
     */
    private final InternPool<Dependency, Dependency> dependencies;

    /**
     * Descriptor interning pool, lives across session (if session carries non-null {@link RepositoryCache}).
     */
    private final InternPool<DescriptorKey, Descriptor> descriptors;

    /**
     * {@link Dependency} list interning pool, lives across session (if session carries non-null {@link RepositoryCache}).
     */
    private final InternPool<List<Dependency>, List<Dependency>> dependencyLists;

    /**
     * Constraint cache, lives during single collection invocation (same as this DataPool instance).
     */
    private final ConcurrentHashMap<Object, Constraint> constraints;

    /**
     * DependencyNode cache, lives during single collection invocation (same as this DataPool instance).
     */
    private final ConcurrentHashMap<Object, List<DependencyNode>> nodes;

    private final boolean internArtifactDescriptorDependencies;

    private final boolean internArtifactDescriptorManagedDependencies;

    @SuppressWarnings("unchecked")
    public DataPool(RepositorySystemSession session) {
        final RepositoryCache cache = session.getCache();

        internArtifactDescriptorDependencies = ConfigUtils.getBoolean(
                session, false, CONFIG_PROP_COLLECTOR_POOL_INTERN_ARTIFACT_DESCRIPTOR_DEPENDENCIES);
        internArtifactDescriptorManagedDependencies = ConfigUtils.getBoolean(
                session, true, CONFIG_PROP_COLLECTOR_POOL_INTERN_ARTIFACT_DESCRIPTOR_MANAGED_DEPENDENCIES);

        InternPool<Artifact, Artifact> artifactsPool = null;
        InternPool<Dependency, Dependency> dependenciesPool = null;
        InternPool<DescriptorKey, Descriptor> descriptorsPool = null;
        InternPool<List<Dependency>, List<Dependency>> dependencyListsPool = null;
        if (cache != null) {
            artifactsPool = (InternPool<Artifact, Artifact>) cache.get(session, ARTIFACT_POOL);
            dependenciesPool = (InternPool<Dependency, Dependency>) cache.get(session, DEPENDENCY_POOL);
            descriptorsPool = (InternPool<DescriptorKey, Descriptor>) cache.get(session, DESCRIPTORS);
            dependencyListsPool =
                    (InternPool<List<Dependency>, List<Dependency>>) cache.get(session, DEPENDENCY_LISTS_POOL);
        }

        if (artifactsPool == null) {
            String artifactPoolType = ConfigUtils.getString(session, WEAK, CONFIG_PROP_COLLECTOR_POOL_ARTIFACT);

            artifactsPool = createPool(artifactPoolType);
            if (cache != null) {
                cache.put(session, ARTIFACT_POOL, artifactsPool);
            }
        }

        if (dependenciesPool == null) {
            String dependencyPoolType = ConfigUtils.getString(session, WEAK, CONFIG_PROP_COLLECTOR_POOL_DEPENDENCY);

            dependenciesPool = createPool(dependencyPoolType);
            if (cache != null) {
                cache.put(session, DEPENDENCY_POOL, dependenciesPool);
            }
        }

        if (descriptorsPool == null) {
            String descriptorPoolType = ConfigUtils.getString(session, HARD, CONFIG_PROP_COLLECTOR_POOL_DESCRIPTOR);

            descriptorsPool = createPool(descriptorPoolType);
            if (cache != null) {
                cache.put(session, DESCRIPTORS, descriptorsPool);
            }
        }

        if (dependencyListsPool == null) {
            String dependencyListsPoolType =
                    ConfigUtils.getString(session, HARD, CONFIG_PROP_COLLECTOR_POOL_DEPENDENCY_LISTS);

            dependencyListsPool = createPool(dependencyListsPoolType);
            if (cache != null) {
                cache.put(session, DEPENDENCY_LISTS_POOL, dependencyListsPool);
            }
        }

        this.artifacts = artifactsPool;
        this.dependencies = dependenciesPool;
        this.descriptors = descriptorsPool;
        this.dependencyLists = dependencyListsPool;

        this.constraints = new ConcurrentHashMap<>(256);
        this.nodes = new ConcurrentHashMap<>(256);
    }

    public Artifact intern(Artifact artifact) {
        return artifacts.intern(artifact, artifact);
    }

    public Dependency intern(Dependency dependency) {
        return dependencies.intern(dependency, dependency);
    }

    public DescriptorKey toKey(ArtifactDescriptorRequest request) {
        return new DescriptorKey(request.getArtifact());
    }

    public ArtifactDescriptorResult getDescriptor(DescriptorKey key, ArtifactDescriptorRequest request) {
        Descriptor descriptor = descriptors.get(key);
        if (descriptor != null) {
            return descriptor.toResult(request);
        }
        return null;
    }

    public void putDescriptor(DescriptorKey key, ArtifactDescriptorResult result) {
        if (internArtifactDescriptorDependencies) {
            result.setDependencies(intern(result.getDependencies()));
        }
        if (internArtifactDescriptorManagedDependencies) {
            result.setManagedDependencies(intern(result.getManagedDependencies()));
        }
        descriptors.intern(key, new GoodDescriptor(result));
    }

    public void putDescriptor(DescriptorKey key, ArtifactDescriptorException e) {
        descriptors.intern(key, BadDescriptor.INSTANCE);
    }

    private List<Dependency> intern(List<Dependency> dependencies) {
        return dependencyLists.intern(dependencies, dependencies);
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

    public static final class DescriptorKey {
        private final Artifact artifact;
        private final int hashCode;

        private DescriptorKey(Artifact artifact) {
            this.artifact = artifact;
            this.hashCode = Objects.hashCode(artifact);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DescriptorKey that = (DescriptorKey) o;
            return Objects.equals(artifact, that.artifact);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{" + "artifact='" + artifact + '\'' + '}';
        }
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

            hashCode =
                    Objects.hash(artifact, repositories, selector, System.identityHashCode(manager), traverser, filter);
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

    private static <K, V> InternPool<K, V> createPool(String type) {
        if (HARD.equals(type)) {
            return new HardInternPool<>();
        } else if (WEAK.equals(type)) {
            return new WeakInternPool<>();
        } else {
            throw new IllegalArgumentException("Unknown object pool type: '" + type + "'");
        }
    }

    public static final String HARD = "hard";

    public static final String WEAK = "weak";

    private interface InternPool<K, V> {
        V get(K key);

        V intern(K key, V value);
    }

    private static class HardInternPool<K, V> implements InternPool<K, V> {
        private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>(256);

        @Override
        public V get(K key) {
            return map.get(key);
        }

        @Override
        public V intern(K key, V value) {
            return map.computeIfAbsent(key, k -> value);
        }
    }

    private static class WeakInternPool<K, V> implements InternPool<K, V> {
        private final Map<K, WeakReference<V>> map = Collections.synchronizedMap(new WeakHashMap<>(256));

        @Override
        public V get(K key) {
            WeakReference<V> ref = map.get(key);
            return ref != null ? ref.get() : null;
        }

        @Override
        public V intern(K key, V value) {
            WeakReference<V> pooledRef = map.get(key);
            if (pooledRef != null) {
                V pooled = pooledRef.get();
                if (pooled != null) {
                    return pooled;
                }
            }
            map.put(key, new WeakReference<>(value));
            return value;
        }
    }
}
