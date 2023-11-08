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
package org.eclipse.aether.internal.impl.session;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystemSession.CloseableRepositorySystemSession;
import org.eclipse.aether.SessionData;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.impl.RepositorySystemLifecycle;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.transfer.TransferListener;

import static java.util.Objects.requireNonNull;

/**
 * A default implementation of repository system session that is immutable.
 */
public final class DefaultCloseableRepositorySystemSession implements CloseableRepositorySystemSession {
    private final AtomicBoolean closed;

    private final String sessionId;

    private final boolean offline;

    private final boolean ignoreArtifactDescriptorRepositories;

    private final ResolutionErrorPolicy resolutionErrorPolicy;

    private final ArtifactDescriptorPolicy artifactDescriptorPolicy;

    private final String checksumPolicy;

    private final String artifactUpdatePolicy;

    private final String metadataUpdatePolicy;

    private final LocalRepositoryManager localRepositoryManager;

    private final WorkspaceReader workspaceReader;

    private final RepositoryListener repositoryListener;

    private final TransferListener transferListener;

    private final Map<String, String> systemProperties;

    private final Map<String, String> userProperties;

    private final Map<String, Object> configProperties;

    private final MirrorSelector mirrorSelector;

    private final ProxySelector proxySelector;

    private final AuthenticationSelector authenticationSelector;

    private final ArtifactTypeRegistry artifactTypeRegistry;

    private final DependencyTraverser dependencyTraverser;

    private final DependencyManager dependencyManager;

    private final DependencySelector dependencySelector;

    private final VersionFilter versionFilter;

    private final DependencyGraphTransformer dependencyGraphTransformer;

    private final SessionData data;

    private final RepositoryCache cache;

    private final RepositorySystemLifecycle repositorySystemLifecycle;

    @SuppressWarnings("checkstyle:parameternumber")
    public DefaultCloseableRepositorySystemSession(
            String sessionId,
            boolean offline,
            boolean ignoreArtifactDescriptorRepositories,
            ResolutionErrorPolicy resolutionErrorPolicy,
            ArtifactDescriptorPolicy artifactDescriptorPolicy,
            String checksumPolicy,
            String artifactUpdatePolicy,
            String metadataUpdatePolicy,
            LocalRepositoryManager localRepositoryManager,
            WorkspaceReader workspaceReader,
            RepositoryListener repositoryListener,
            TransferListener transferListener,
            Map<String, String> systemProperties,
            Map<String, String> userProperties,
            Map<String, Object> configProperties,
            MirrorSelector mirrorSelector,
            ProxySelector proxySelector,
            AuthenticationSelector authenticationSelector,
            ArtifactTypeRegistry artifactTypeRegistry,
            DependencyTraverser dependencyTraverser,
            DependencyManager dependencyManager,
            DependencySelector dependencySelector,
            VersionFilter versionFilter,
            DependencyGraphTransformer dependencyGraphTransformer,
            SessionData data,
            RepositoryCache cache,
            RepositorySystemLifecycle repositorySystemLifecycle) {
        this.closed = new AtomicBoolean(false);
        this.sessionId = requireNonNull(sessionId);
        this.offline = offline;
        this.ignoreArtifactDescriptorRepositories = ignoreArtifactDescriptorRepositories;
        this.resolutionErrorPolicy = resolutionErrorPolicy;
        this.artifactDescriptorPolicy = artifactDescriptorPolicy;
        this.checksumPolicy = checksumPolicy;
        this.artifactUpdatePolicy = artifactUpdatePolicy;
        this.metadataUpdatePolicy = metadataUpdatePolicy;
        this.localRepositoryManager = requireNonNull(localRepositoryManager);
        this.workspaceReader = workspaceReader;
        this.repositoryListener = repositoryListener;
        this.transferListener = transferListener;
        this.systemProperties = Collections.unmodifiableMap(systemProperties);
        this.userProperties = Collections.unmodifiableMap(userProperties);
        this.configProperties = Collections.unmodifiableMap(configProperties);
        this.mirrorSelector = requireNonNull(mirrorSelector);
        this.proxySelector = requireNonNull(proxySelector);
        this.authenticationSelector = requireNonNull(authenticationSelector);
        this.artifactTypeRegistry = requireNonNull(artifactTypeRegistry);
        this.dependencyTraverser = dependencyTraverser;
        this.dependencyManager = dependencyManager;
        this.dependencySelector = dependencySelector;
        this.versionFilter = versionFilter;
        this.dependencyGraphTransformer = dependencyGraphTransformer;
        this.data = requireNonNull(data);
        this.cache = cache;
        this.repositorySystemLifecycle = requireNonNull(repositorySystemLifecycle);

        repositorySystemLifecycle.sessionStarted(this);
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public boolean isOffline() {
        return offline;
    }

    @Override
    public boolean isIgnoreArtifactDescriptorRepositories() {
        return ignoreArtifactDescriptorRepositories;
    }

    @Override
    public ResolutionErrorPolicy getResolutionErrorPolicy() {
        return resolutionErrorPolicy;
    }

    @Override
    public ArtifactDescriptorPolicy getArtifactDescriptorPolicy() {
        return artifactDescriptorPolicy;
    }

    @Override
    public String getChecksumPolicy() {
        return checksumPolicy;
    }

    @Override
    public String getUpdatePolicy() {
        return getArtifactUpdatePolicy();
    }

    @Override
    public String getArtifactUpdatePolicy() {
        return artifactUpdatePolicy;
    }

    @Override
    public String getMetadataUpdatePolicy() {
        return metadataUpdatePolicy;
    }

    @Override
    public LocalRepository getLocalRepository() {
        return getLocalRepositoryManager().getRepository();
    }

    @Override
    public LocalRepositoryManager getLocalRepositoryManager() {
        return localRepositoryManager;
    }

    @Override
    public WorkspaceReader getWorkspaceReader() {
        return workspaceReader;
    }

    @Override
    public RepositoryListener getRepositoryListener() {
        return repositoryListener;
    }

    @Override
    public TransferListener getTransferListener() {
        return transferListener;
    }

    @Override
    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    @Override
    public Map<String, String> getUserProperties() {
        return userProperties;
    }

    @Override
    public Map<String, Object> getConfigProperties() {
        return configProperties;
    }

    @Override
    public MirrorSelector getMirrorSelector() {
        return mirrorSelector;
    }

    @Override
    public ProxySelector getProxySelector() {
        return proxySelector;
    }

    @Override
    public AuthenticationSelector getAuthenticationSelector() {
        return authenticationSelector;
    }

    @Override
    public ArtifactTypeRegistry getArtifactTypeRegistry() {
        return artifactTypeRegistry;
    }

    @Override
    public DependencyTraverser getDependencyTraverser() {
        return dependencyTraverser;
    }

    @Override
    public DependencyManager getDependencyManager() {
        return dependencyManager;
    }

    @Override
    public DependencySelector getDependencySelector() {
        return dependencySelector;
    }

    @Override
    public VersionFilter getVersionFilter() {
        return versionFilter;
    }

    @Override
    public DependencyGraphTransformer getDependencyGraphTransformer() {
        return dependencyGraphTransformer;
    }

    @Override
    public SessionData getData() {
        return data;
    }

    @Override
    public RepositoryCache getCache() {
        return cache;
    }

    @Override
    public boolean addOnSessionEndedHandler(Runnable handler) {
        throwIfClosed();
        repositorySystemLifecycle.addOnSessionEndedHandle(this, handler);
        return true;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            repositorySystemLifecycle.sessionEnded(this);
        }
    }

    private void throwIfClosed() {
        if (closed.get()) {
            throw new IllegalStateException("Session " + sessionId + " already closed");
        }
    }
}
