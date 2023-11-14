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
import java.util.Collection;
import java.util.List;
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
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.graph.visitor.FilteringDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.LevelOrderDependencyNodeConsumerVisitor;
import org.eclipse.aether.util.graph.visitor.PostorderDependencyNodeConsumerVisitor;
import org.eclipse.aether.util.graph.visitor.PreorderDependencyNodeConsumerVisitor;

import static java.util.Objects.requireNonNull;

/**
 *
 */
@Singleton
@Named
public class DefaultRepositorySystem implements RepositorySystem {
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
            RepositorySystemLifecycle repositorySystemLifecycle) {
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
    }

    @Override
    public VersionResult resolveVersion(RepositorySystemSession session, VersionRequest request)
            throws VersionResolutionException {
        validateSession(session);
        requireNonNull(request, "request cannot be null");

        return versionResolver.resolveVersion(session, request);
    }

    @Override
    public VersionRangeResult resolveVersionRange(RepositorySystemSession session, VersionRangeRequest request)
            throws VersionRangeResolutionException {
        validateSession(session);
        requireNonNull(request, "request cannot be null");

        return versionRangeResolver.resolveVersionRange(session, request);
    }

    @Override
    public ArtifactDescriptorResult readArtifactDescriptor(
            RepositorySystemSession session, ArtifactDescriptorRequest request) throws ArtifactDescriptorException {
        validateSession(session);
        requireNonNull(request, "request cannot be null");

        return artifactDescriptorReader.readArtifactDescriptor(session, request);
    }

    @Override
    public ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request)
            throws ArtifactResolutionException {
        validateSession(session);
        requireNonNull(request, "request cannot be null");

        return artifactResolver.resolveArtifact(session, request);
    }

    @Override
    public List<ArtifactResult> resolveArtifacts(
            RepositorySystemSession session, Collection<? extends ArtifactRequest> requests)
            throws ArtifactResolutionException {
        validateSession(session);
        requireNonNull(requests, "requests cannot be null");

        return artifactResolver.resolveArtifacts(session, requests);
    }

    @Override
    public List<MetadataResult> resolveMetadata(
            RepositorySystemSession session, Collection<? extends MetadataRequest> requests) {
        validateSession(session);
        requireNonNull(requests, "requests cannot be null");

        return metadataResolver.resolveMetadata(session, requests);
    }

    @Override
    public CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)
            throws DependencyCollectionException {
        validateSession(session);
        requireNonNull(request, "request cannot be null");

        return dependencyCollector.collectDependencies(session, request);
    }

    @Override
    public DependencyResult resolveDependencies(RepositorySystemSession session, DependencyRequest request)
            throws DependencyResolutionException {
        validateSession(session);
        requireNonNull(request, "request cannot be null");

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

        final ArrayList<DependencyNode> dependencyNodes = new ArrayList<>();
        DependencyVisitor builder = getDependencyVisitor(session, dependencyNodes::add);
        DependencyFilter filter = request.getFilter();
        DependencyVisitor visitor = (filter != null) ? new FilteringDependencyVisitor(builder, filter) : builder;
        if (result.getRoot() != null) {
            result.getRoot().accept(visitor);
        }

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
    }

    @Override
    public List<DependencyNode> flattenDependencyNodes(RepositorySystemSession session, DependencyNode root) {
        validateSession(session);
        requireNonNull(root, "root cannot be null");

        final ArrayList<DependencyNode> dependencyNodes = new ArrayList<>();
        root.accept(getDependencyVisitor(session, dependencyNodes::add));
        return dependencyNodes;
    }

    private DependencyVisitor getDependencyVisitor(
            RepositorySystemSession session, Consumer<DependencyNode> nodeConsumer) {
        String strategy = ConfigUtils.getString(
                session,
                ConfigurationProperties.DEFAULT_REPOSITORY_SYSTEM_RESOLVER_DEPENDENCIES_VISITOR,
                ConfigurationProperties.REPOSITORY_SYSTEM_RESOLVER_DEPENDENCIES_VISITOR);
        switch (strategy) {
            case PreorderDependencyNodeConsumerVisitor.NAME:
                return new PreorderDependencyNodeConsumerVisitor(nodeConsumer);
            case PostorderDependencyNodeConsumerVisitor.NAME:
                return new PostorderDependencyNodeConsumerVisitor(nodeConsumer);
            case LevelOrderDependencyNodeConsumerVisitor.NAME:
                return new LevelOrderDependencyNodeConsumerVisitor(nodeConsumer);
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

        return installer.install(session, request);
    }

    @Override
    public DeployResult deploy(RepositorySystemSession session, DeployRequest request) throws DeploymentException {
        validateSession(session);
        requireNonNull(request, "request cannot be null");

        return deployer.deploy(session, request);
    }

    @Override
    public LocalRepositoryManager newLocalRepositoryManager(
            RepositorySystemSession session, LocalRepository localRepository) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(localRepository, "localRepository cannot be null");
        validateSystem();

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

        repositories = remoteRepositoryManager.aggregateRepositories(session, new ArrayList<>(), repositories, true);
        return repositories;
    }

    @Override
    public RemoteRepository newDeploymentRepository(RepositorySystemSession session, RemoteRepository repository) {
        validateSession(session);
        requireNonNull(repository, "repository cannot be null");

        RemoteRepository.Builder builder = new RemoteRepository.Builder(repository);
        Authentication auth = session.getAuthenticationSelector().getAuthentication(repository);
        builder.setAuthentication(auth);
        Proxy proxy = session.getProxySelector().getProxy(repository);
        builder.setProxy(proxy);
        return builder.build();
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
