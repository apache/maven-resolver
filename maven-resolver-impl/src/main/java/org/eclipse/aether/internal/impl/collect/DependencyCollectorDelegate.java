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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.DependencyCollector;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.scope.InternalScopeManager;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.scope.ResolutionScope;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.graph.transformer.TransformationContextKeys;
import org.eclipse.aether.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Helper class for delegate implementations, they MUST subclass this class.
 *
 * @since 1.8.0
 */
public abstract class DependencyCollectorDelegate implements DependencyCollector {
    /**
     * Only exceptions up to the number given in this configuration property are emitted. Exceptions which exceed
     * that number are swallowed.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_MAX_EXCEPTIONS}
     */
    public static final String CONFIG_PROP_MAX_EXCEPTIONS =
            DefaultDependencyCollector.CONFIG_PROPS_PREFIX + "maxExceptions";

    public static final int DEFAULT_MAX_EXCEPTIONS = 50;

    /**
     * Only up to the given amount cyclic dependencies are emitted.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_MAX_CYCLES}
     */
    public static final String CONFIG_PROP_MAX_CYCLES = DefaultDependencyCollector.CONFIG_PROPS_PREFIX + "maxCycles";

    public static final int DEFAULT_MAX_CYCLES = 10;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final RemoteRepositoryManager remoteRepositoryManager;

    protected final ArtifactDescriptorReader descriptorReader;

    protected final VersionRangeResolver versionRangeResolver;

    protected DependencyCollectorDelegate(
            RemoteRepositoryManager remoteRepositoryManager,
            ArtifactDescriptorReader artifactDescriptorReader,
            VersionRangeResolver versionRangeResolver) {
        this.remoteRepositoryManager =
                requireNonNull(remoteRepositoryManager, "remote repository manager cannot be null");
        this.descriptorReader = requireNonNull(artifactDescriptorReader, "artifact descriptor reader cannot be null");
        this.versionRangeResolver = requireNonNull(versionRangeResolver, "version range resolver cannot be null");
    }

    @SuppressWarnings("checkstyle:methodlength")
    @Override
    public final CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)
            throws DependencyCollectionException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");

        InternalScopeManager scopeManager = (InternalScopeManager) session.getScopeManager();
        session = setUpSession(session, request, scopeManager);

        RequestTrace trace = RequestTrace.newChild(request.getTrace(), request);

        CollectResult result = new CollectResult(request);

        DependencyTraverser depTraverser = session.getDependencyTraverser();
        VersionFilter verFilter = session.getVersionFilter();

        Dependency root = request.getRoot();
        List<RemoteRepository> repositories = request.getRepositories();
        List<Dependency> dependencies = request.getDependencies();
        List<Dependency> managedDependencies = request.getManagedDependencies();

        Map<String, Object> stats = new LinkedHashMap<>();
        long time1 = System.nanoTime();

        DefaultDependencyNode node;
        if (root != null) {
            List<? extends Version> versions;
            VersionRangeResult rangeResult;
            try {
                VersionRangeRequest rangeRequest = new VersionRangeRequest(
                        root.getArtifact(), request.getRepositories(), request.getRequestContext());
                rangeRequest.setTrace(trace);
                rangeResult = versionRangeResolver.resolveVersionRange(session, rangeRequest);
                versions = filterVersions(root, rangeResult, verFilter, new DefaultVersionFilterContext(session));
            } catch (VersionRangeResolutionException e) {
                result.addException(e);
                throw new DependencyCollectionException(result, e.getMessage());
            }

            Version version = versions.get(versions.size() - 1);
            root = root.setArtifact(root.getArtifact().setVersion(version.toString()));

            ArtifactDescriptorResult descriptorResult;
            try {
                ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
                descriptorRequest.setArtifact(root.getArtifact());
                descriptorRequest.setRepositories(request.getRepositories());
                descriptorRequest.setRequestContext(request.getRequestContext());
                descriptorRequest.setTrace(trace);
                if (isLackingDescriptor(session, root.getArtifact())) {
                    descriptorResult = new ArtifactDescriptorResult(descriptorRequest);
                } else {
                    descriptorResult = descriptorReader.readArtifactDescriptor(session, descriptorRequest);
                }
            } catch (ArtifactDescriptorException e) {
                result.addException(e);
                throw new DependencyCollectionException(result, e.getMessage());
            }

            root = root.setArtifact(descriptorResult.getArtifact());

            if (!session.isIgnoreArtifactDescriptorRepositories()) {
                repositories = remoteRepositoryManager.aggregateRepositories(
                        session, repositories, descriptorResult.getRepositories(), true);
            }
            dependencies = mergeDeps(dependencies, descriptorResult.getDependencies());
            managedDependencies = mergeDeps(managedDependencies, descriptorResult.getManagedDependencies());

            node = new DefaultDependencyNode(root);
            node.setRequestContext(request.getRequestContext());
            node.setRelocations(descriptorResult.getRelocations());
            node.setVersionConstraint(rangeResult.getVersionConstraint());
            node.setVersion(version);
            node.setAliases(descriptorResult.getAliases());
            node.setRepositories(request.getRepositories());
        } else {
            node = new DefaultDependencyNode(request.getRootArtifact());
            node.setRequestContext(request.getRequestContext());
            node.setRepositories(request.getRepositories());
        }

        result.setRoot(node);

        boolean traverse = root == null || depTraverser == null || depTraverser.traverseDependency(root);
        String errorPath = null;
        if (traverse && !dependencies.isEmpty()) {
            DataPool pool = new DataPool(session);

            DefaultDependencyCollectionContext context = new DefaultDependencyCollectionContext(
                    session, request.getRootArtifact(), root, managedDependencies);

            DefaultVersionFilterContext versionContext = new DefaultVersionFilterContext(session);

            Results results = new Results(result, session);

            doCollectDependencies(
                    session,
                    trace,
                    pool,
                    context,
                    versionContext,
                    request,
                    node,
                    repositories,
                    dependencies,
                    managedDependencies,
                    results);

            errorPath = results.getErrorPath();
        }

        long time2 = System.nanoTime();

        DependencyGraphTransformer transformer = session.getDependencyGraphTransformer();
        if (transformer != null) {
            try {
                DefaultDependencyGraphTransformationContext context =
                        new DefaultDependencyGraphTransformationContext(session);
                context.put(TransformationContextKeys.STATS, stats);
                result.setRoot(transformer.transformGraph(node, context));
            } catch (RepositoryException e) {
                result.addException(e);
            }
        }

        long time3 = System.nanoTime();
        if (logger.isDebugEnabled()) {
            stats.put(getClass().getSimpleName() + ".collectTime", time2 - time1);
            stats.put(getClass().getSimpleName() + ".transformTime", time3 - time2);
            logger.debug("Dependency collection stats {}", stats);
        }

        if (errorPath != null) {
            throw new DependencyCollectionException(result, "Failed to collect dependencies at " + errorPath);
        }
        if (!result.getExceptions().isEmpty()) {
            throw new DependencyCollectionException(result);
        }

        if (request.getResolutionScope() != null) {
            return scopeManager.postProcess(request.getResolutionScope(), result);
        } else {
            return result;
        }
    }

    /**
     * Creates child {@link RequestTrace} instance from passed in {@link RequestTrace} and parameters by creating
     * {@link CollectStepDataImpl} instance out of passed in data. Caller must ensure that passed in parameters are
     * NOT affected by threading (or that there is no multi threading involved). In other words, the passed in values
     * should be immutable.
     *
     * @param trace   The current trace instance.
     * @param context The context from {@link CollectRequest#getRequestContext()}, never {@code null}.
     * @param path    List representing the path of dependency nodes, never {@code null}. Caller must ensure, that this
     *                list does not change during the lifetime of the requested {@link RequestTrace} instance. If it may
     *                change, simplest is to pass here a copy of used list.
     * @param node    Currently collected node, that collector came by following the passed in path.
     * @return A child request trance instance, never {@code null}.
     */
    protected RequestTrace collectStepTrace(
            RequestTrace trace, String context, List<DependencyNode> path, Dependency node) {
        return RequestTrace.newChild(trace, new CollectStepDataImpl(context, path, node));
    }

    @SuppressWarnings("checkstyle:parameternumber")
    protected abstract void doCollectDependencies(
            RepositorySystemSession session,
            RequestTrace trace,
            DataPool pool,
            DefaultDependencyCollectionContext context,
            DefaultVersionFilterContext versionContext,
            CollectRequest request,
            DependencyNode node,
            List<RemoteRepository> repositories,
            List<Dependency> dependencies,
            List<Dependency> managedDependencies,
            Results results)
            throws DependencyCollectionException;

    protected RepositorySystemSession setUpSession(
            RepositorySystemSession session, CollectRequest collectRequest, InternalScopeManager scopeManager) {
        DefaultRepositorySystemSession optimized = new DefaultRepositorySystemSession(session);
        optimized.setArtifactTypeRegistry(CachingArtifactTypeRegistry.newInstance(session));

        ResolutionScope resolutionScope = collectRequest.getResolutionScope();
        if (resolutionScope != null) {
            requireNonNull(scopeManager, "ScopeManager is not set on session");
            optimized.setDependencySelector(scopeManager.getDependencySelector(resolutionScope));
            optimized.setDependencyGraphTransformer(scopeManager.getDependencyGraphTransformer(resolutionScope));
        }
        return optimized;
    }

    protected List<Dependency> mergeDeps(List<Dependency> dominant, List<Dependency> recessive) {
        List<Dependency> result;
        if (dominant == null || dominant.isEmpty()) {
            result = recessive;
        } else if (recessive == null || recessive.isEmpty()) {
            result = dominant;
        } else {
            int initialCapacity = dominant.size() + recessive.size();
            result = new ArrayList<>(initialCapacity);
            Collection<String> ids = new HashSet<>(initialCapacity, 1.0f);
            for (Dependency dependency : dominant) {
                ids.add(getId(dependency.getArtifact()));
                result.add(dependency);
            }
            for (Dependency dependency : recessive) {
                if (!ids.contains(getId(dependency.getArtifact()))) {
                    result.add(dependency);
                }
            }
        }
        return result;
    }

    protected static String getId(Artifact a) {
        return a.getGroupId() + ':' + a.getArtifactId() + ':' + a.getClassifier() + ':' + a.getExtension();
    }

    @SuppressWarnings("checkstyle:parameternumber")
    protected static DefaultDependencyNode createDependencyNode(
            List<Artifact> relocations,
            PremanagedDependency preManaged,
            VersionRangeResult rangeResult,
            Version version,
            Dependency d,
            Collection<Artifact> aliases,
            List<RemoteRepository> repos,
            String requestContext) {
        DefaultDependencyNode child = new DefaultDependencyNode(d);
        preManaged.applyTo(child);
        child.setRelocations(relocations);
        child.setVersionConstraint(rangeResult.getVersionConstraint());
        child.setVersion(version);
        child.setAliases(aliases);
        child.setRepositories(repos);
        child.setRequestContext(requestContext);
        return child;
    }

    protected static DefaultDependencyNode createDependencyNode(
            List<Artifact> relocations,
            PremanagedDependency preManaged,
            VersionRangeResult rangeResult,
            Version version,
            Dependency d,
            ArtifactDescriptorResult descriptorResult,
            DependencyNode cycleNode) {
        DefaultDependencyNode child = createDependencyNode(
                relocations,
                preManaged,
                rangeResult,
                version,
                d,
                descriptorResult.getAliases(),
                cycleNode.getRepositories(),
                cycleNode.getRequestContext());
        child.setChildren(cycleNode.getChildren());
        return child;
    }

    protected static ArtifactDescriptorRequest createArtifactDescriptorRequest(
            String requestContext, RequestTrace requestTrace, List<RemoteRepository> repositories, Dependency d) {
        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(d.getArtifact());
        descriptorRequest.setRepositories(repositories);
        descriptorRequest.setRequestContext(requestContext);
        descriptorRequest.setTrace(requestTrace);
        return descriptorRequest;
    }

    protected static VersionRangeRequest createVersionRangeRequest(
            String requestContext,
            RequestTrace requestTrace,
            List<RemoteRepository> repositories,
            Dependency dependency) {
        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact(dependency.getArtifact());
        rangeRequest.setRepositories(repositories);
        rangeRequest.setRequestContext(requestContext);
        rangeRequest.setTrace(requestTrace);
        return rangeRequest;
    }

    protected VersionRangeResult cachedResolveRangeResult(
            VersionRangeRequest rangeRequest, DataPool pool, RepositorySystemSession session)
            throws VersionRangeResolutionException {
        Object key = pool.toKey(rangeRequest);
        VersionRangeResult rangeResult = pool.getConstraint(key, rangeRequest);
        if (rangeResult == null) {
            rangeResult = versionRangeResolver.resolveVersionRange(session, rangeRequest);
            pool.putConstraint(key, rangeResult);
        }
        return rangeResult;
    }

    protected static boolean isLackingDescriptor(RepositorySystemSession session, Artifact artifact) {
        return session.getSystemScopeHandler().getSystemPath(artifact) != null;
    }

    protected static List<RemoteRepository> getRemoteRepositories(
            ArtifactRepository repository, List<RemoteRepository> repositories) {
        if (repository instanceof RemoteRepository) {
            return Collections.singletonList((RemoteRepository) repository);
        }
        if (repository != null) {
            return Collections.emptyList();
        }
        return repositories;
    }

    protected static List<? extends Version> filterVersions(
            Dependency dependency,
            VersionRangeResult rangeResult,
            VersionFilter verFilter,
            DefaultVersionFilterContext verContext)
            throws VersionRangeResolutionException {
        if (rangeResult.getVersions().isEmpty()) {
            throw new VersionRangeResolutionException(
                    rangeResult, "No versions available for " + dependency.getArtifact() + " within specified range");
        }

        List<? extends Version> versions;
        if (verFilter != null && rangeResult.getVersionConstraint().getRange() != null) {
            verContext.set(dependency, rangeResult);
            try {
                verFilter.filterVersions(verContext);
            } catch (RepositoryException e) {
                throw new VersionRangeResolutionException(
                        rangeResult, "Failed to filter versions for " + dependency.getArtifact(), e);
            }
            versions = verContext.get();
            if (versions.isEmpty()) {
                throw new VersionRangeResolutionException(
                        rangeResult,
                        "No acceptable versions for " + dependency.getArtifact() + ": " + rangeResult.getVersions());
            }
        } else {
            versions = rangeResult.getVersions();
        }
        return versions;
    }

    /**
     * Helper class used during collection.
     */
    protected static class Results {

        private final CollectResult result;

        final int maxExceptions;

        final int maxCycles;

        String errorPath;

        public Results(CollectResult result, RepositorySystemSession session) {
            this.result = result;

            maxExceptions = ConfigUtils.getInteger(session, DEFAULT_MAX_EXCEPTIONS, CONFIG_PROP_MAX_EXCEPTIONS);

            maxCycles = ConfigUtils.getInteger(session, DEFAULT_MAX_CYCLES, CONFIG_PROP_MAX_CYCLES);
        }

        public CollectResult getResult() {
            return result;
        }

        public String getErrorPath() {
            return errorPath;
        }

        public void addException(Dependency dependency, Exception e, List<DependencyNode> nodes) {
            if (maxExceptions < 0 || result.getExceptions().size() < maxExceptions) {
                result.addException(e);
                if (errorPath == null) {
                    StringBuilder buffer = new StringBuilder(256);
                    for (DependencyNode node : nodes) {
                        if (buffer.length() > 0) {
                            buffer.append(" -> ");
                        }
                        Dependency dep = node.getDependency();
                        if (dep != null) {
                            buffer.append(dep.getArtifact());
                        }
                    }
                    if (buffer.length() > 0) {
                        buffer.append(" -> ");
                    }
                    buffer.append(dependency.getArtifact());
                    errorPath = buffer.toString();
                }
            }
        }

        public void addCycle(List<DependencyNode> nodes, int cycleEntry, Dependency dependency) {
            if (maxCycles < 0 || result.getCycles().size() < maxCycles) {
                result.addCycle(new DefaultDependencyCycle(nodes, cycleEntry, dependency));
            }
        }
    }
}
