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
package org.eclipse.aether.internal.impl;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.DependencyCollector;
import org.eclipse.aether.impl.Deployer;
import org.eclipse.aether.impl.Installer;
import org.eclipse.aether.impl.LocalRepositoryProvider;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositorySystemLifecycle;
import org.eclipse.aether.impl.RepositorySystemValidator;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.internal.impl.session.DefaultSessionBuilder;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.spi.artifact.decorator.ArtifactDecorator;
import org.eclipse.aether.spi.artifact.decorator.ArtifactDecoratorFactory;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.graph.visitor.LevelOrderDependencyNodeConsumerVisitor;
import org.eclipse.aether.util.graph.visitor.PostorderDependencyNodeConsumerVisitor;
import org.eclipse.aether.util.graph.visitor.PreorderDependencyNodeConsumerVisitor;
import org.eclipse.aether.util.repository.ChainedLocalRepositoryManager;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 *
 */
@Singleton
@Named
public class DefaultRepositorySystem implements RepositorySystem {
    /**
     * Sentinel object placed into the {@link RequestTrace} chain by each public method
     * to detect re-entrant calls into this {@code RepositorySystem}.
     * <p>
     * The resolver's architecture assumes that internal components ({@code ArtifactResolver},
     * {@code DependencyCollector}, {@code ArtifactDescriptorReader}, etc.) call each other
     * directly via the internal API in {@code org.eclipse.aether.impl}, never going through
     * the public {@code RepositorySystem} facade. This means session validation, request
     * validation, and artifact decoration only need to run once — on the outermost call.
     * <p>
     * However, some {@code RepositorySystem} consumers (notably Maven 4's
     * {@code ArtifactDescriptorReader} → {@code ModelBuilder} → {@code ModelResolver} chain)
     * re-enter {@code RepositorySystem} during an ongoing operation. Without this guard,
     * every re-entrant call redundantly validates the session, validates the request (which
     * may reject intermediate state like uninterpolated expressions from transitive POMs),
     * and applies artifact decorators. This causes both correctness issues (false validation
     * failures) and unnecessary performance overhead.
     * <p>
     * Re-entrancy can happen on the <em>same</em> thread (e.g. {@code collectDependencies}
     * → {@code readArtifactDescriptor} → model builder → model resolver → {@code resolveVersionRange})
     * or on a <em>different</em> thread when the resolver uses internal parallelism (e.g.
     * {@code BfDependencyCollector}'s {@code SmartExecutor} dispatches descriptor resolution
     * to pool threads, which then re-enter {@code RepositorySystem} via the model resolver).
     * <p>
     * To handle both cases, we leverage the {@link RequestTrace} chain that is already
     * propagated across threads by the caller (e.g. Maven's model builder explicitly copies
     * traces to pool threads). On the outermost call, we stamp this marker into the request's
     * trace. On re-entry — whether on the same thread or a pool thread — the marker is found
     * in the trace ancestry, so validation and decoration are skipped.
     */
    private static final Object REPOSITORY_SYSTEM_CALL = new Object() {
        @Override
        public String toString() {
            return "RepositorySystem";
        }
    };

    private final AtomicBoolean shutdown;

    private final AtomicInteger sessionIdCounter;

    private final VersionResolver versionResolver;

    private final VersionRangeResolver versionRangeResolver;

    private final ArtifactResolver artifactResolver;

    private final MetadataResolver metadataResolver;

    private final ArtifactDescriptorReader artifactDescriptorReader;

    private final DependencyCollector dependencyCollector;

    private final Installer installer;

    private final Deployer deployer;

    private final LocalRepositoryProvider localRepositoryProvider;

    private final SyncContextFactory syncContextFactory;

    private final RemoteRepositoryManager remoteRepositoryManager;

    private final RepositorySystemLifecycle repositorySystemLifecycle;

    private final Map<String, ArtifactDecoratorFactory> artifactDecoratorFactories;

    private final RepositorySystemValidator repositorySystemValidator;

    @SuppressWarnings("checkstyle:parameternumber")
    @Inject
    public DefaultRepositorySystem(
            VersionResolver versionResolver,
            VersionRangeResolver versionRangeResolver,
            ArtifactResolver artifactResolver,
            MetadataResolver metadataResolver,
            ArtifactDescriptorReader artifactDescriptorReader,
            DependencyCollector dependencyCollector,
            Installer installer,
            Deployer deployer,
            LocalRepositoryProvider localRepositoryProvider,
            SyncContextFactory syncContextFactory,
            RemoteRepositoryManager remoteRepositoryManager,
            RepositorySystemLifecycle repositorySystemLifecycle,
            Map<String, ArtifactDecoratorFactory> artifactDecoratorFactories,
            RepositorySystemValidator repositorySystemValidator) {
        this.shutdown = new AtomicBoolean(false);
        this.sessionIdCounter = new AtomicInteger(0);
        this.versionResolver = requireNonNull(versionResolver, "version resolver cannot be null");
        this.versionRangeResolver = requireNonNull(versionRangeResolver, "version range resolver cannot be null");
        this.artifactResolver = requireNonNull(artifactResolver, "artifact resolver cannot be null");
        this.metadataResolver = requireNonNull(metadataResolver, "metadata resolver cannot be null");
        this.artifactDescriptorReader =
                requireNonNull(artifactDescriptorReader, "artifact descriptor reader cannot be null");
        this.dependencyCollector = requireNonNull(dependencyCollector, "dependency collector cannot be null");
        this.installer = requireNonNull(installer, "installer cannot be null");
        this.deployer = requireNonNull(deployer, "deployer cannot be null");
        this.localRepositoryProvider =
                requireNonNull(localRepositoryProvider, "local repository provider cannot be null");
        this.syncContextFactory = requireNonNull(syncContextFactory, "sync context factory cannot be null");
        this.remoteRepositoryManager =
                requireNonNull(remoteRepositoryManager, "remote repository provider cannot be null");
        this.repositorySystemLifecycle =
                requireNonNull(repositorySystemLifecycle, "repository system lifecycle cannot be null");
        this.artifactDecoratorFactories =
                requireNonNull(artifactDecoratorFactories, "artifact decorator factories cannot be null");
        this.repositorySystemValidator =
                requireNonNull(repositorySystemValidator, "repository system validator cannot be null");
    }

    @Override
    public VersionResult resolveVersion(RepositorySystemSession session, VersionRequest request)
            throws VersionResolutionException {
        requireNonNull(request, "request cannot be null");
        Runnable exitGuard = null;
        if (!isReentrant(request.getTrace(), session)) {
            validateSession(session);
            repositorySystemValidator.validateVersionRequest(session, request);
            request.setTrace(stampReentrancyMarker(request.getTrace()));
            exitGuard = enterSessionScope(session);
        }
        try {
            return versionResolver.resolveVersion(session, request);
        } finally {
            if (exitGuard != null) {
                exitGuard.run();
            }
        }
    }

    @Override
    public VersionRangeResult resolveVersionRange(RepositorySystemSession session, VersionRangeRequest request)
            throws VersionRangeResolutionException {
        requireNonNull(request, "request cannot be null");
        Runnable exitGuard = null;
        if (!isReentrant(request.getTrace(), session)) {
            validateSession(session);
            repositorySystemValidator.validateVersionRangeRequest(session, request);
            request.setTrace(stampReentrancyMarker(request.getTrace()));
            exitGuard = enterSessionScope(session);
        }
        try {
            return versionRangeResolver.resolveVersionRange(session, request);
        } finally {
            if (exitGuard != null) {
                exitGuard.run();
            }
        }
    }

    @Override
    public ArtifactDescriptorResult readArtifactDescriptor(
            RepositorySystemSession session, ArtifactDescriptorRequest request) throws ArtifactDescriptorException {
        requireNonNull(request, "request cannot be null");
        boolean outermost = !isReentrant(request.getTrace(), session);
        Runnable exitGuard = null;
        if (outermost) {
            validateSession(session);
            repositorySystemValidator.validateArtifactDescriptorRequest(session, request);
            request.setTrace(stampReentrancyMarker(request.getTrace()));
            exitGuard = enterSessionScope(session);
        }
        try {
            ArtifactDescriptorResult descriptorResult =
                    artifactDescriptorReader.readArtifactDescriptor(session, request);
            if (outermost) {
                for (ArtifactDecorator decorator : Utils.getArtifactDecorators(session, artifactDecoratorFactories)) {
                    descriptorResult.setArtifact(decorator.decorateArtifact(descriptorResult));
                }
            }
            return descriptorResult;
        } finally {
            if (exitGuard != null) {
                exitGuard.run();
            }
        }
    }

    @Override
    public ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request)
            throws ArtifactResolutionException {
        requireNonNull(request, "request cannot be null");
        Runnable exitGuard = null;
        if (!isReentrant(request.getTrace(), session)) {
            validateSession(session);
            repositorySystemValidator.validateArtifactRequests(session, Collections.singleton(request));
            request.setTrace(stampReentrancyMarker(request.getTrace()));
            exitGuard = enterSessionScope(session);
        }
        try {
            return artifactResolver.resolveArtifact(session, request);
        } finally {
            if (exitGuard != null) {
                exitGuard.run();
            }
        }
    }

    @Override
    public List<ArtifactResult> resolveArtifacts(
            RepositorySystemSession session, Collection<? extends ArtifactRequest> requests)
            throws ArtifactResolutionException {
        requireNonNull(requests, "requests cannot be null");
        // All requests in a batch share the same trace context, so checking any one is sufficient.
        RequestTrace firstTrace = requests.stream()
                .map(ArtifactRequest::getTrace)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        Runnable exitGuard = null;
        if (!isReentrant(firstTrace, session)) {
            validateSession(session);
            repositorySystemValidator.validateArtifactRequests(session, requests);
            for (ArtifactRequest request : requests) {
                request.setTrace(stampReentrancyMarker(request.getTrace()));
            }
            exitGuard = enterSessionScope(session);
        }
        try {
            return artifactResolver.resolveArtifacts(session, requests);
        } finally {
            if (exitGuard != null) {
                exitGuard.run();
            }
        }
    }

    @Override
    public List<MetadataResult> resolveMetadata(
            RepositorySystemSession session, Collection<? extends MetadataRequest> requests) {
        requireNonNull(requests, "requests cannot be null");
        // All requests in a batch share the same trace context, so checking any one is sufficient.
        RequestTrace firstTrace = requests.stream()
                .map(MetadataRequest::getTrace)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        Runnable exitGuard = null;
        if (!isReentrant(firstTrace, session)) {
            validateSession(session);
            repositorySystemValidator.validateMetadataRequests(session, requests);
            for (MetadataRequest request : requests) {
                request.setTrace(stampReentrancyMarker(request.getTrace()));
            }
            exitGuard = enterSessionScope(session);
        }
        try {
            return metadataResolver.resolveMetadata(session, requests);
        } finally {
            if (exitGuard != null) {
                exitGuard.run();
            }
        }
    }

    @Override
    public CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)
            throws DependencyCollectionException {
        requireNonNull(request, "request cannot be null");
        Runnable exitGuard = null;
        if (!isReentrant(request.getTrace(), session)) {
            validateSession(session);
            repositorySystemValidator.validateCollectRequest(session, request);
            request.setTrace(stampReentrancyMarker(request.getTrace()));
            exitGuard = enterSessionScope(session);
        }
        try {
            return dependencyCollector.collectDependencies(session, request);
        } finally {
            if (exitGuard != null) {
                exitGuard.run();
            }
        }
    }

    @Override
    public DependencyResult resolveDependencies(RepositorySystemSession session, DependencyRequest request)
            throws DependencyResolutionException {
        requireNonNull(request, "request cannot be null");
        Runnable exitGuard = null;
        if (!isReentrant(request.getTrace(), session)) {
            validateSession(session);
            repositorySystemValidator.validateDependencyRequest(session, request);
            request.setTrace(stampReentrancyMarker(request.getTrace()));
            exitGuard = enterSessionScope(session);
        }
        try {
            RequestTrace trace = RequestTrace.newChild(request.getTrace(), request);

            DependencyResult result = new DependencyResult(request);

            DependencyCollectionException dce = null;
            ArtifactResolutionException are = null;

            if (request.getRoot() != null) {
                result.setRoot(request.getRoot());
            } else if (request.getCollectRequest() != null) {
                CollectResult collectResult;
                try {
                    request.getCollectRequest().setTrace(trace);
                    collectResult = dependencyCollector.collectDependencies(session, request.getCollectRequest());
                } catch (DependencyCollectionException e) {
                    dce = e;
                    collectResult = e.getResult();
                }
                result.setRoot(collectResult.getRoot());
                result.setCycles(collectResult.getCycles());
                result.setCollectExceptions(collectResult.getExceptions());
            } else {
                throw new NullPointerException("dependency node and collect request cannot be null");
            }

            final List<DependencyNode> dependencyNodes =
                    doFlattenDependencyNodes(session, result.getRoot(), request.getFilter());

            final List<ArtifactRequest> requests = dependencyNodes.stream()
                    .map(n -> {
                        if (n.getDependency() != null) {
                            ArtifactRequest artifactRequest = new ArtifactRequest(n);
                            artifactRequest.setTrace(trace);
                            return artifactRequest;
                        } else {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            List<ArtifactResult> results;
            try {
                results = artifactResolver.resolveArtifacts(session, requests);
            } catch (ArtifactResolutionException e) {
                are = e;
                results = e.getResults();
            }
            result.setDependencyNodeResults(dependencyNodes);
            result.setArtifactResults(results);

            updateNodesWithResolvedArtifacts(results);

            if (dce != null) {
                throw new DependencyResolutionException(result, dce);
            } else if (are != null) {
                throw new DependencyResolutionException(result, are);
            }

            return result;
        } finally {
            if (exitGuard != null) {
                exitGuard.run();
            }
        }
    }

    @Override
    public List<DependencyNode> flattenDependencyNodes(
            RepositorySystemSession session, DependencyNode root, DependencyFilter dependencyFilter) {
        validateSession(session);
        requireNonNull(root, "root cannot be null");
        return doFlattenDependencyNodes(session, root, dependencyFilter);
    }

    private List<DependencyNode> doFlattenDependencyNodes(
            RepositorySystemSession session, DependencyNode root, DependencyFilter dependencyFilter) {
        final ArrayList<DependencyNode> dependencyNodes = new ArrayList<>();
        if (root != null) {
            root.accept(getDependencyVisitor(session, dependencyNodes::add, dependencyFilter));
        }
        return dependencyNodes;
    }

    private DependencyVisitor getDependencyVisitor(
            RepositorySystemSession session, Consumer<DependencyNode> nodeConsumer, DependencyFilter dependencyFilter) {
        String strategy = ConfigUtils.getString(
                session,
                ConfigurationProperties.DEFAULT_REPOSITORY_SYSTEM_DEPENDENCY_VISITOR,
                ConfigurationProperties.REPOSITORY_SYSTEM_DEPENDENCY_VISITOR);
        switch (strategy) {
            case PreorderDependencyNodeConsumerVisitor.NAME:
                return new PreorderDependencyNodeConsumerVisitor(nodeConsumer, dependencyFilter);
            case PostorderDependencyNodeConsumerVisitor.NAME:
                return new PostorderDependencyNodeConsumerVisitor(nodeConsumer, dependencyFilter);
            case LevelOrderDependencyNodeConsumerVisitor.NAME:
                return new LevelOrderDependencyNodeConsumerVisitor(nodeConsumer, dependencyFilter);
            default:
                throw new IllegalArgumentException("Invalid dependency visitor strategy: " + strategy);
        }
    }

    private void updateNodesWithResolvedArtifacts(List<ArtifactResult> results) {
        for (ArtifactResult result : results) {
            Artifact artifact = result.getArtifact();
            if (artifact != null) {
                result.getRequest().getDependencyNode().setArtifact(artifact);
            }
        }
    }

    @Override
    public InstallResult install(RepositorySystemSession session, InstallRequest request) throws InstallationException {
        validateSession(session);
        requireNonNull(request, "request cannot be null");
        repositorySystemValidator.validateInstallRequest(session, request);
        return installer.install(session, request);
    }

    @Override
    public DeployResult deploy(RepositorySystemSession session, DeployRequest request) throws DeploymentException {
        validateSession(session);
        requireNonNull(request, "request cannot be null");
        repositorySystemValidator.validateDeployRequest(session, request);
        return deployer.deploy(session, request);
    }

    @Override
    public LocalRepositoryManager newLocalRepositoryManager(
            RepositorySystemSession session, LocalRepository localRepository) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(localRepository, "localRepository cannot be null");
        validateSystem();
        repositorySystemValidator.validateLocalRepositories(session, Collections.singleton(localRepository));
        return createLocalRepositoryManager(session, localRepository);
    }

    @Override
    public LocalRepositoryManager newLocalRepositoryManager(
            RepositorySystemSession session, LocalRepository... localRepositories) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(localRepositories, "localRepositories cannot be null");
        validateSystem();
        repositorySystemValidator.validateLocalRepositories(session, Arrays.asList(localRepositories));
        return createLocalRepositoryManager(session, Arrays.asList(localRepositories));
    }

    @Override
    public LocalRepositoryManager newLocalRepositoryManager(
            RepositorySystemSession session, List<LocalRepository> localRepositories) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(localRepositories, "localRepositories cannot be null");
        validateSystem();
        repositorySystemValidator.validateLocalRepositories(session, localRepositories);
        return createLocalRepositoryManager(session, localRepositories);
    }

    private LocalRepositoryManager createLocalRepositoryManager(
            RepositorySystemSession session, List<LocalRepository> localRepositories) {
        if (localRepositories.isEmpty()) {
            throw new IllegalArgumentException("empty localRepositories");
        } else if (localRepositories.size() == 1) {
            return createLocalRepositoryManager(session, localRepositories.get(0));
        } else {
            LocalRepositoryManager head = createLocalRepositoryManager(session, localRepositories.get(0));
            List<LocalRepositoryManager> tail = localRepositories.subList(1, localRepositories.size()).stream()
                    .map(l -> createLocalRepositoryManager(session, l))
                    .collect(toList());
            return new ChainedLocalRepositoryManager(head, tail, session);
        }
    }

    private LocalRepositoryManager createLocalRepositoryManager(
            RepositorySystemSession session, LocalRepository localRepository) {
        try {
            return localRepositoryProvider.newLocalRepositoryManager(session, localRepository);
        } catch (NoLocalRepositoryManagerException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public SyncContext newSyncContext(RepositorySystemSession session, boolean shared) {
        validateSession(session);
        return syncContextFactory.newInstance(session, shared);
    }

    @Override
    public List<RemoteRepository> newResolutionRepositories(
            RepositorySystemSession session, List<RemoteRepository> repositories) {
        validateSession(session);
        validateRepositories(repositories);
        repositorySystemValidator.validateRemoteRepositories(session, repositories);
        return remoteRepositoryManager.aggregateRepositories(session, new ArrayList<>(), repositories, true);
    }

    @Override
    public RemoteRepository newDeploymentRepository(RepositorySystemSession session, RemoteRepository repository) {
        validateSession(session);
        requireNonNull(repository, "repository cannot be null");
        repositorySystemValidator.validateRemoteRepositories(session, Collections.singletonList(repository));
        Authentication auth = session.getAuthenticationSelector().getAuthentication(repository);
        Proxy proxy = session.getProxySelector().getProxy(repository);
        return new RemoteRepository.Builder(repository)
                .setAuthentication(auth)
                .setProxy(proxy)
                .setIntent(RemoteRepository.Intent.DEPLOYMENT)
                .build();
    }

    @Override
    public void addOnSystemEndedHandler(Runnable handler) {
        validateSystem();
        repositorySystemLifecycle.addOnSystemEndedHandler(handler);
    }

    @Override
    public RepositorySystemSession.SessionBuilder createSessionBuilder() {
        validateSystem();
        return new DefaultSessionBuilder(
                this, repositorySystemLifecycle, () -> "id-" + sessionIdCounter.incrementAndGet());
    }

    @Override
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            repositorySystemLifecycle.systemEnded();
        }
    }

    /**
     * Session data key for the re-entrancy depth counter. This supplements the
     * {@link RequestTrace}-based detection for consumers that rebuild the trace chain
     * from a different tracing system (e.g. Maven 4's {@code RequestTraceHelper} converts
     * between Maven API traces and resolver traces, losing the
     * {@link #REPOSITORY_SYSTEM_CALL} marker).
     * <p>
     * The value stored under this key is an {@link AtomicInteger} tracking how many
     * {@code RepositorySystem} public methods are currently on the call stack for this
     * session. A value &gt; 0 on entry means the call is re-entrant.
     */
    private static final Object SESSION_REENTRY_DEPTH_KEY = new Object() {
        @Override
        public String toString() {
            return "RepositorySystem.reentryDepth";
        }
    };

    /**
     * Stamps the {@link #REPOSITORY_SYSTEM_CALL} re-entrancy marker into the trace chain
     * while preserving the original trace tip data. The marker is inserted <em>below</em>
     * the tip so that code walking the trace and casting {@code getData()} to its expected
     * type (e.g. {@code org.apache.maven.artifact.Artifact}) still finds the original data
     * at the tip rather than the anonymous marker object.
     *
     * @param currentTrace the current request trace (may be {@code null})
     * @return a new trace with the marker inserted and original tip data preserved
     */
    private static RequestTrace stampReentrancyMarker(RequestTrace currentTrace) {
        RequestTrace markerTrace = RequestTrace.newChild(currentTrace, REPOSITORY_SYSTEM_CALL);
        return currentTrace != null ? RequestTrace.newChild(markerTrace, currentTrace.getData()) : markerTrace;
    }

    /**
     * Checks whether the given {@link RequestTrace} indicates a re-entrant call by looking
     * for the {@link #REPOSITORY_SYSTEM_CALL} marker in the trace ancestry.
     *
     * @return {@code true} if the marker is found (re-entrant call), {@code false} otherwise
     */
    private static boolean isReentrant(RequestTrace trace) {
        for (RequestTrace t = trace; t != null; t = t.getParent()) {
            if (t.getData() == REPOSITORY_SYSTEM_CALL) {
                return true;
            }
        }
        return false;
    }

    /**
     * Combined re-entrancy check using both {@link RequestTrace} ancestry and session-scoped
     * depth tracking. Either mechanism detecting re-entrancy is sufficient to skip validation.
     * <p>
     * The trace-based check is the primary mechanism and works when callers properly propagate
     * traces. The session-based check is a fallback for callers that rebuild the trace chain
     * from a different tracing system (e.g. Maven 4's trace conversion loses the resolver's
     * re-entrancy marker).
     *
     * @param trace   the current request trace (may be {@code null})
     * @param session the current repository system session
     * @return {@code true} if this is a re-entrant call, {@code false} if it is the outermost call
     */
    private static boolean isReentrant(RequestTrace trace, RepositorySystemSession session) {
        return isReentrant(trace) || getReentryDepth(session).get() > 0;
    }

    /**
     * Increments the session-scoped re-entrancy depth counter. Must be called on every outermost
     * entry into a public {@code RepositorySystem} method, and the returned {@link Runnable} must
     * be invoked in a {@code finally} block to decrement the counter on exit.
     *
     * @param session the current repository system session
     * @return a {@link Runnable} that decrements the depth counter when invoked
     */
    private static Runnable enterSessionScope(RepositorySystemSession session) {
        AtomicInteger depth = getReentryDepth(session);
        depth.incrementAndGet();
        return depth::decrementAndGet;
    }

    private static AtomicInteger getReentryDepth(RepositorySystemSession session) {
        return (AtomicInteger) session.getData().computeIfAbsent(SESSION_REENTRY_DEPTH_KEY, () -> new AtomicInteger(0));
    }

    private void validateSession(RepositorySystemSession session) {
        requireNonNull(session, "repository system session cannot be null");
        invalidSession(session.getLocalRepositoryManager(), "local repository manager");
        invalidSession(session.getSystemProperties(), "system properties");
        invalidSession(session.getUserProperties(), "user properties");
        invalidSession(session.getConfigProperties(), "config properties");
        invalidSession(session.getMirrorSelector(), "mirror selector");
        invalidSession(session.getProxySelector(), "proxy selector");
        invalidSession(session.getAuthenticationSelector(), "authentication selector");
        invalidSession(session.getArtifactTypeRegistry(), "artifact type registry");
        invalidSession(session.getData(), "data");
        validateSystem();
    }

    private void validateSystem() {
        if (shutdown.get()) {
            throw new IllegalStateException("repository system is already shut down");
        }
    }

    private void validateRepositories(List<RemoteRepository> repositories) {
        requireNonNull(repositories, "repositories cannot be null");
        for (RemoteRepository repository : repositories) {
            requireNonNull(repository, "repository cannot be null");
        }
    }

    private void invalidSession(Object obj, String name) {
        requireNonNull(obj, "repository system session's " + name + " cannot be null");
    }
}
