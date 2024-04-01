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
package org.eclipse.aether.internal.impl.collect.bf;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.internal.impl.collect.DataPool;
import org.eclipse.aether.internal.impl.collect.DefaultDependencyCollectionContext;
import org.eclipse.aether.internal.impl.collect.DefaultDependencyCollector;
import org.eclipse.aether.internal.impl.collect.DefaultVersionFilterContext;
import org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegate;
import org.eclipse.aether.internal.impl.collect.PremanagedDependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.concurrency.ExecutorUtils;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.version.Version;

import static org.eclipse.aether.internal.impl.collect.DefaultDependencyCycle.find;

/**
 * Breadth-first {@link org.eclipse.aether.impl.DependencyCollector}
 *
 * @since 1.8.0
 */
@Singleton
@Named(BfDependencyCollector.NAME)
public class BfDependencyCollector extends DependencyCollectorDelegate {
    public static final String NAME = "bf";

    private static final String CONFIG_PROPS_PREFIX = DefaultDependencyCollector.CONFIG_PROPS_PREFIX + NAME + ".";

    /**
     * The key in the repository session's {@link RepositorySystemSession#getConfigProperties()
     * configuration properties} used to store a {@link Boolean} flag controlling the resolver's skip mode.
     *
     * @since 1.8.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_SKIPPER}
     */
    public static final String CONFIG_PROP_SKIPPER = CONFIG_PROPS_PREFIX + "skipper";

    /**
     * The default value for {@link #CONFIG_PROP_SKIPPER}, {@code true}.
     *
     * @since 1.8.0
     */
    public static final boolean DEFAULT_SKIPPER = true;

    /**
     * The count of threads to be used when collecting POMs in parallel.
     *
     * @since 1.9.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_THREADS}
     */
    public static final String CONFIG_PROP_THREADS = CONFIG_PROPS_PREFIX + "threads";

    /**
     * The default value for {@link #CONFIG_PROP_THREADS}, default value 5.
     *
     * @since 1.9.0
     */
    public static final int DEFAULT_THREADS = 5;

    @Inject
    public BfDependencyCollector(
            RemoteRepositoryManager remoteRepositoryManager,
            ArtifactDescriptorReader artifactDescriptorReader,
            VersionRangeResolver versionRangeResolver) {
        super(remoteRepositoryManager, artifactDescriptorReader, versionRangeResolver);
    }

    @SuppressWarnings("checkstyle:parameternumber")
    @Override
    protected void doCollectDependencies(
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
            throws DependencyCollectionException {
        boolean useSkip = ConfigUtils.getBoolean(session, DEFAULT_SKIPPER, CONFIG_PROP_SKIPPER);
        int nThreads = ExecutorUtils.threadCount(session, DEFAULT_THREADS, CONFIG_PROP_THREADS);
        logger.debug("Using thread pool with {} threads to resolve descriptors.", nThreads);

        if (useSkip) {
            logger.debug("Collector skip mode enabled");
        }

        try (DependencyResolutionSkipper skipper = useSkip
                        ? DependencyResolutionSkipper.defaultSkipper()
                        : DependencyResolutionSkipper.neverSkipper();
                ParallelDescriptorResolver parallelDescriptorResolver = new ParallelDescriptorResolver(nThreads)) {
            Args args = new Args(session, pool, context, versionContext, request, skipper, parallelDescriptorResolver);

            DependencySelector rootDepSelector = session.getDependencySelector() != null
                    ? session.getDependencySelector().deriveChildSelector(context)
                    : null;
            DependencyManager rootDepManager = session.getDependencyManager() != null
                    ? session.getDependencyManager().deriveChildManager(context)
                    : null;
            DependencyTraverser rootDepTraverser = session.getDependencyTraverser() != null
                    ? session.getDependencyTraverser().deriveChildTraverser(context)
                    : null;
            VersionFilter rootVerFilter = session.getVersionFilter() != null
                    ? session.getVersionFilter().deriveChildFilter(context)
                    : null;

            List<DependencyNode> parents = Collections.singletonList(node);
            for (Dependency dependency : dependencies) {
                RequestTrace childTrace =
                        collectStepTrace(trace, args.request.getRequestContext(), parents, dependency);
                DependencyProcessingContext processingContext = new DependencyProcessingContext(
                        rootDepSelector,
                        rootDepManager,
                        rootDepTraverser,
                        rootVerFilter,
                        childTrace,
                        repositories,
                        managedDependencies,
                        parents,
                        dependency,
                        PremanagedDependency.create(rootDepManager, dependency, false, args.premanagedState));
                if (!filter(processingContext)) {
                    processingContext.withDependency(processingContext.premanagedDependency.getManagedDependency());
                    resolveArtifactDescriptorAsync(args, processingContext, results);
                    args.dependencyProcessingQueue.add(processingContext);
                }
            }

            while (!args.dependencyProcessingQueue.isEmpty()) {
                processDependency(
                        args, results, args.dependencyProcessingQueue.remove(), Collections.emptyList(), false);
            }

            if (args.interruptedException.get() != null) {
                throw new DependencyCollectionException(
                        results.getResult(), "Collection interrupted", args.interruptedException.get());
            }
        }
    }

    @SuppressWarnings("checkstyle:parameternumber")
    private void processDependency(
            Args args,
            Results results,
            DependencyProcessingContext context,
            List<Artifact> relocations,
            boolean disableVersionManagement) {
        if (Thread.interrupted()) {
            args.interruptedException.set(new InterruptedException());
        }
        if (args.interruptedException.get() != null) {
            return;
        }
        Dependency dependency = context.dependency;
        PremanagedDependency preManaged = context.premanagedDependency;

        boolean noDescriptor = isLackingDescriptor(args.session, dependency.getArtifact());
        boolean traverse =
                !noDescriptor && (context.depTraverser == null || context.depTraverser.traverseDependency(dependency));

        Future<DescriptorResolutionResult> resolutionResultFuture = args.resolver.find(dependency.getArtifact());
        DescriptorResolutionResult resolutionResult;
        VersionRangeResult rangeResult;
        try {
            resolutionResult = resolutionResultFuture.get();
            rangeResult = resolutionResult.rangeResult;
        } catch (Exception e) {
            results.addException(dependency, e, context.parents);
            return;
        }

        Set<Version> versions = resolutionResult.descriptors.keySet();
        for (Version version : versions) {
            Artifact originalArtifact = dependency.getArtifact().setVersion(version.toString());
            Dependency d = dependency.setArtifact(originalArtifact);

            final ArtifactDescriptorResult descriptorResult = resolutionResult.descriptors.get(version);
            if (descriptorResult != null) {
                d = d.setArtifact(descriptorResult.getArtifact());

                int cycleEntry = find(context.parents, d.getArtifact());
                if (cycleEntry >= 0) {
                    results.addCycle(context.parents, cycleEntry, d);
                    DependencyNode cycleNode = context.parents.get(cycleEntry);
                    if (cycleNode.getDependency() != null) {
                        DefaultDependencyNode child = createDependencyNode(
                                relocations, preManaged, rangeResult, version, d, descriptorResult, cycleNode);
                        context.getParent().getChildren().add(child);
                        continue;
                    }
                }

                if (!descriptorResult.getRelocations().isEmpty()) {
                    boolean disableVersionManagementSubsequently =
                            originalArtifact.getGroupId().equals(d.getArtifact().getGroupId())
                                    && originalArtifact
                                            .getArtifactId()
                                            .equals(d.getArtifact().getArtifactId());

                    PremanagedDependency premanagedDependency = PremanagedDependency.create(
                            context.depManager, d, disableVersionManagementSubsequently, args.premanagedState);
                    DependencyProcessingContext relocatedContext = new DependencyProcessingContext(
                            context.depSelector,
                            context.depManager,
                            context.depTraverser,
                            context.verFilter,
                            context.trace,
                            context.repositories,
                            descriptorResult.getManagedDependencies(),
                            context.parents,
                            d,
                            premanagedDependency);

                    if (!filter(relocatedContext)) {
                        relocatedContext.withDependency(premanagedDependency.getManagedDependency());
                        resolveArtifactDescriptorAsync(args, relocatedContext, results);
                        processDependency(
                                args,
                                results,
                                relocatedContext,
                                descriptorResult.getRelocations(),
                                disableVersionManagementSubsequently);
                    }

                    return;
                } else {
                    d = args.pool.intern(d.setArtifact(args.pool.intern(d.getArtifact())));

                    List<RemoteRepository> repos =
                            getRemoteRepositories(rangeResult.getRepository(version), context.repositories);

                    DefaultDependencyNode child = createDependencyNode(
                            relocations,
                            preManaged,
                            rangeResult,
                            version,
                            d,
                            descriptorResult.getAliases(),
                            repos,
                            args.request.getRequestContext());

                    context.getParent().getChildren().add(child);

                    boolean recurse =
                            traverse && !descriptorResult.getDependencies().isEmpty();
                    DependencyProcessingContext parentContext = context.withDependency(d);
                    if (recurse) {
                        doRecurse(args, parentContext, descriptorResult, child, results, disableVersionManagement);
                    } else if (!args.skipper.skipResolution(child, parentContext.parents)) {
                        List<DependencyNode> parents = new ArrayList<>(parentContext.parents.size() + 1);
                        parents.addAll(parentContext.parents);
                        parents.add(child);
                        args.skipper.cache(child, parents);
                    }
                }
            } else {
                List<RemoteRepository> repos =
                        getRemoteRepositories(rangeResult.getRepository(version), context.repositories);
                DefaultDependencyNode child = createDependencyNode(
                        relocations,
                        preManaged,
                        rangeResult,
                        version,
                        d,
                        null,
                        repos,
                        args.request.getRequestContext());
                context.getParent().getChildren().add(child);
            }
        }
    }

    @SuppressWarnings("checkstyle:parameternumber")
    private void doRecurse(
            Args args,
            DependencyProcessingContext parentContext,
            ArtifactDescriptorResult descriptorResult,
            DefaultDependencyNode child,
            Results results,
            boolean disableVersionManagement) {
        DefaultDependencyCollectionContext context = args.collectionContext;
        context.set(parentContext.dependency, descriptorResult.getManagedDependencies());

        DependencySelector childSelector =
                parentContext.depSelector != null ? parentContext.depSelector.deriveChildSelector(context) : null;
        DependencyManager childManager =
                parentContext.depManager != null ? parentContext.depManager.deriveChildManager(context) : null;
        DependencyTraverser childTraverser =
                parentContext.depTraverser != null ? parentContext.depTraverser.deriveChildTraverser(context) : null;
        VersionFilter childFilter =
                parentContext.verFilter != null ? parentContext.verFilter.deriveChildFilter(context) : null;

        final List<RemoteRepository> childRepos = args.ignoreRepos
                ? parentContext.repositories
                : remoteRepositoryManager.aggregateRepositories(
                        args.session, parentContext.repositories, descriptorResult.getRepositories(), true);

        Object key = args.pool.toKey(
                parentContext.dependency.getArtifact(),
                childRepos,
                childSelector,
                childManager,
                childTraverser,
                childFilter);

        List<DependencyNode> children = args.pool.getChildren(key);
        if (children == null) {
            boolean skipResolution = args.skipper.skipResolution(child, parentContext.parents);
            if (!skipResolution) {
                List<DependencyNode> parents = new ArrayList<>(parentContext.parents.size() + 1);
                parents.addAll(parentContext.parents);
                parents.add(child);
                for (Dependency dependency : descriptorResult.getDependencies()) {
                    RequestTrace childTrace = collectStepTrace(
                            parentContext.trace, args.request.getRequestContext(), parents, dependency);
                    PremanagedDependency premanagedDependency = PremanagedDependency.create(
                            childManager, dependency, disableVersionManagement, args.premanagedState);
                    DependencyProcessingContext processingContext = new DependencyProcessingContext(
                            childSelector,
                            childManager,
                            childTraverser,
                            childFilter,
                            childTrace,
                            childRepos,
                            descriptorResult.getManagedDependencies(),
                            parents,
                            dependency,
                            premanagedDependency);
                    if (!filter(processingContext)) {
                        // resolve descriptors ahead for managed dependency
                        processingContext.withDependency(processingContext.premanagedDependency.getManagedDependency());
                        resolveArtifactDescriptorAsync(args, processingContext, results);
                        args.dependencyProcessingQueue.add(processingContext);
                    }
                }
                args.pool.putChildren(key, child.getChildren());
                args.skipper.cache(child, parents);
            }
        } else {
            child.setChildren(children);
        }
    }

    private boolean filter(DependencyProcessingContext context) {
        return context.depSelector != null && !context.depSelector.selectDependency(context.dependency);
    }

    private void resolveArtifactDescriptorAsync(Args args, DependencyProcessingContext context, Results results) {
        Dependency dependency = context.dependency;
        args.resolver.resolveDescriptors(dependency.getArtifact(), () -> {
            VersionRangeRequest rangeRequest = createVersionRangeRequest(
                    args.request.getRequestContext(), context.trace, context.repositories, dependency);
            VersionRangeResult rangeResult = cachedResolveRangeResult(rangeRequest, args.pool, args.session);
            List<? extends Version> versions =
                    filterVersions(dependency, rangeResult, context.verFilter, args.versionContext);

            // resolve newer version first to maximize benefits of skipper
            Collections.reverse(versions);

            Map<Version, ArtifactDescriptorResult> descriptors = new ConcurrentHashMap<>(versions.size());
            Stream<? extends Version> stream = versions.size() > 1 ? versions.parallelStream() : versions.stream();
            stream.forEach(version -> Optional.ofNullable(
                            resolveDescriptorForVersion(args, context, results, dependency, version))
                    .ifPresent(r -> descriptors.put(version, r)));

            DescriptorResolutionResult resolutionResult =
                    new DescriptorResolutionResult(dependency.getArtifact(), rangeResult);
            // keep original sequence
            versions.forEach(version -> resolutionResult.descriptors.put(version, descriptors.get(version)));
            // populate for versions in version range
            resolutionResult.flatten().forEach(dr -> args.resolver.cacheVersionRangeDescriptor(dr.artifact, dr));

            return resolutionResult;
        });
    }

    private ArtifactDescriptorResult resolveDescriptorForVersion(
            Args args, DependencyProcessingContext context, Results results, Dependency dependency, Version version) {
        Artifact original = dependency.getArtifact();
        Artifact newArtifact = new DefaultArtifact(
                original.getGroupId(),
                original.getArtifactId(),
                original.getClassifier(),
                original.getExtension(),
                version.toString(),
                original.getProperties(),
                (ArtifactType) null);
        Dependency newDependency =
                new Dependency(newArtifact, dependency.getScope(), dependency.isOptional(), dependency.getExclusions());
        DependencyProcessingContext newContext = context.copy();

        ArtifactDescriptorRequest descriptorRequest = createArtifactDescriptorRequest(
                args.request.getRequestContext(), context.trace, newContext.repositories, newDependency);
        return isLackingDescriptor(args.session, newArtifact)
                ? new ArtifactDescriptorResult(descriptorRequest)
                : resolveCachedArtifactDescriptor(
                        args.pool,
                        descriptorRequest,
                        args.session,
                        newContext.withDependency(newDependency).dependency,
                        results,
                        context.parents);
    }

    static class ParallelDescriptorResolver implements Closeable {
        private final ExecutorService executorService;

        /**
         * Artifact ID -> Future of DescriptorResolutionResult
         */
        private final Map<String, Future<DescriptorResolutionResult>> results = new ConcurrentHashMap<>(256);

        ParallelDescriptorResolver(int threads) {
            this.executorService = ExecutorUtils.threadPool(threads, getClass().getSimpleName() + "-");
        }

        void resolveDescriptors(Artifact artifact, Callable<DescriptorResolutionResult> callable) {
            results.computeIfAbsent(ArtifactIdUtils.toId(artifact), key -> this.executorService.submit(callable));
        }

        void cacheVersionRangeDescriptor(Artifact artifact, DescriptorResolutionResult resolutionResult) {
            results.computeIfAbsent(ArtifactIdUtils.toId(artifact), key -> new DoneFuture<>(resolutionResult));
        }

        Future<DescriptorResolutionResult> find(Artifact artifact) {
            return results.get(ArtifactIdUtils.toId(artifact));
        }

        @Override
        public void close() {
            executorService.shutdown();
        }
    }

    static class DoneFuture<V> implements Future<V> {
        private final V v;

        DoneFuture(V v) {
            this.v = v;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return v;
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return v;
        }
    }

    static class DescriptorResolutionResult {
        Artifact artifact;

        VersionRangeResult rangeResult;

        Map<Version, ArtifactDescriptorResult> descriptors;

        DescriptorResolutionResult(Artifact artifact, VersionRangeResult rangeResult) {
            this.artifact = artifact;
            this.rangeResult = rangeResult;
            this.descriptors = new LinkedHashMap<>(rangeResult.getVersions().size());
        }

        DescriptorResolutionResult(
                VersionRangeResult rangeResult, Version version, ArtifactDescriptorResult descriptor) {
            // NOTE: In case of A1 -> A2 relocation this happens:
            // ArtifactDescriptorResult read by ArtifactDescriptorResultReader for A1
            // will return instance that will have artifact = A2 (as RelocatedArtifact).
            // So to properly "key" this instance, we need to use "originally requested" A1 instead!
            // In short:
            // ArtifactDescriptorRequest.artifact != ArtifactDescriptorResult.artifact WHEN relocation in play
            // otherwise (no relocation), they are EQUAL.
            this(descriptor.getRequest().getArtifact(), rangeResult);
            this.descriptors.put(version, descriptor);
        }

        List<DescriptorResolutionResult> flatten() {
            if (descriptors.size() > 1) {
                return descriptors.entrySet().stream()
                        .map(e -> new DescriptorResolutionResult(rangeResult, e.getKey(), e.getValue()))
                        .collect(Collectors.toList());
            } else {
                return Collections.emptyList();
            }
        }
    }

    static class Args {

        final RepositorySystemSession session;

        final boolean ignoreRepos;

        final boolean premanagedState;

        final DataPool pool;

        final Queue<DependencyProcessingContext> dependencyProcessingQueue = new ArrayDeque<>(128);

        final DefaultDependencyCollectionContext collectionContext;

        final DefaultVersionFilterContext versionContext;

        final CollectRequest request;

        final DependencyResolutionSkipper skipper;

        final ParallelDescriptorResolver resolver;

        final AtomicReference<InterruptedException> interruptedException;

        Args(
                RepositorySystemSession session,
                DataPool pool,
                DefaultDependencyCollectionContext collectionContext,
                DefaultVersionFilterContext versionContext,
                CollectRequest request,
                DependencyResolutionSkipper skipper,
                ParallelDescriptorResolver resolver) {
            this.session = session;
            this.request = request;
            this.ignoreRepos = session.isIgnoreArtifactDescriptorRepositories();
            this.premanagedState = ConfigUtils.getBoolean(session, false, DependencyManagerUtils.CONFIG_PROP_VERBOSE);
            this.pool = pool;
            this.collectionContext = collectionContext;
            this.versionContext = versionContext;
            this.skipper = skipper;
            this.resolver = resolver;
            this.interruptedException = new AtomicReference<>(null);
        }
    }
}
