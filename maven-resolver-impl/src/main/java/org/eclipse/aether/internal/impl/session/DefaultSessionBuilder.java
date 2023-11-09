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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.aether.DefaultSessionData;
import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession.SessionBuilder;
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
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.transfer.TransferListener;

import static java.util.Objects.requireNonNull;

/**
 * A default implementation of session builder. Is not immutable nor thread-safe.
 */
public final class DefaultSessionBuilder implements SessionBuilder {
    private static final MirrorSelector NULL_MIRROR_SELECTOR = r -> null;

    private static final ProxySelector NULL_PROXY_SELECTOR = RemoteRepository::getProxy;

    private static final AuthenticationSelector NULL_AUTHENTICATION_SELECTOR = RemoteRepository::getAuthentication;

    private static final ArtifactTypeRegistry NULL_ARTIFACT_TYPE_REGISTRY = t -> null;

    private final RepositorySystem repositorySystem;

    private final RepositorySystemLifecycle repositorySystemLifecycle;

    private final String sessionId;

    private final AtomicBoolean closed;

    private final ArrayList<Runnable> onCloseHandler;

    private boolean offline;

    private boolean ignoreArtifactDescriptorRepositories;

    private ResolutionErrorPolicy resolutionErrorPolicy;

    private ArtifactDescriptorPolicy artifactDescriptorPolicy;

    private String checksumPolicy;

    private String artifactUpdatePolicy;

    private String metadataUpdatePolicy;

    private LocalRepositoryManager localRepositoryManager;

    private WorkspaceReader workspaceReader;

    private RepositoryListener repositoryListener;

    private TransferListener transferListener;

    private Map<String, String> systemProperties = new HashMap<>();

    private Map<String, String> userProperties = new HashMap<>();

    private Map<String, Object> configProperties = new HashMap<>();

    private MirrorSelector mirrorSelector = NULL_MIRROR_SELECTOR;

    private ProxySelector proxySelector = NULL_PROXY_SELECTOR;

    private AuthenticationSelector authenticationSelector = NULL_AUTHENTICATION_SELECTOR;

    private ArtifactTypeRegistry artifactTypeRegistry = NULL_ARTIFACT_TYPE_REGISTRY;

    private DependencyTraverser dependencyTraverser;

    private DependencyManager dependencyManager;

    private DependencySelector dependencySelector;

    private VersionFilter versionFilter;

    private DependencyGraphTransformer dependencyGraphTransformer;

    private SessionData data = new DefaultSessionData();

    private RepositoryCache cache;

    public DefaultSessionBuilder(
            RepositorySystem repositorySystem,
            RepositorySystemLifecycle repositorySystemLifecycle,
            String sessionId,
            AtomicBoolean closed) {
        this.repositorySystem = requireNonNull(repositorySystem);
        this.repositorySystemLifecycle = requireNonNull(repositorySystemLifecycle);
        this.sessionId = requireNonNull(sessionId);
        this.closed = closed;
        this.onCloseHandler = new ArrayList<>();
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
        return localRepositoryManager.getRepository();
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
        requireNonNull(handler, "null handler");
        onCloseHandler.add(handler);
        return true;
    }

    @Override
    public DefaultSessionBuilder setOffline(boolean offline) {
        this.offline = offline;
        return this;
    }

    @Override
    public DefaultSessionBuilder setIgnoreArtifactDescriptorRepositories(boolean ignoreArtifactDescriptorRepositories) {
        this.ignoreArtifactDescriptorRepositories = ignoreArtifactDescriptorRepositories;
        return this;
    }

    @Override
    public DefaultSessionBuilder setResolutionErrorPolicy(ResolutionErrorPolicy resolutionErrorPolicy) {
        this.resolutionErrorPolicy = resolutionErrorPolicy;
        return this;
    }

    @Override
    public DefaultSessionBuilder setArtifactDescriptorPolicy(ArtifactDescriptorPolicy artifactDescriptorPolicy) {
        this.artifactDescriptorPolicy = artifactDescriptorPolicy;
        return this;
    }

    @Override
    public DefaultSessionBuilder setChecksumPolicy(String checksumPolicy) {
        this.checksumPolicy = checksumPolicy;
        return this;
    }

    @Override
    public DefaultSessionBuilder setUpdatePolicy(String updatePolicy) {
        setArtifactUpdatePolicy(updatePolicy);
        setMetadataUpdatePolicy(updatePolicy);
        return this;
    }

    @Override
    public DefaultSessionBuilder setArtifactUpdatePolicy(String artifactUpdatePolicy) {
        this.artifactUpdatePolicy = artifactUpdatePolicy;
        return this;
    }

    @Override
    public DefaultSessionBuilder setMetadataUpdatePolicy(String metadataUpdatePolicy) {
        this.metadataUpdatePolicy = metadataUpdatePolicy;
        return this;
    }

    @Override
    public DefaultSessionBuilder setLocalRepositoryManager(LocalRepositoryManager localRepositoryManager) {
        this.localRepositoryManager = localRepositoryManager;
        return this;
    }

    @Override
    public DefaultSessionBuilder setWorkspaceReader(WorkspaceReader workspaceReader) {
        this.workspaceReader = workspaceReader;
        return this;
    }

    @Override
    public DefaultSessionBuilder setRepositoryListener(RepositoryListener repositoryListener) {
        this.repositoryListener = repositoryListener;
        return this;
    }

    @Override
    public DefaultSessionBuilder setTransferListener(TransferListener transferListener) {
        this.transferListener = transferListener;
        return this;
    }

    @Override
    public DefaultSessionBuilder setSystemProperties(Map<?, ?> systemProperties) {
        this.systemProperties = copySafe(systemProperties, String.class);
        return this;
    }

    @Override
    public DefaultSessionBuilder setSystemProperty(String key, String value) {
        if (value != null) {
            systemProperties.put(key, value);
        } else {
            systemProperties.remove(key);
        }
        return this;
    }

    @Override
    public DefaultSessionBuilder setUserProperties(Map<?, ?> userProperties) {
        this.userProperties = copySafe(userProperties, String.class);
        return this;
    }

    @Override
    public DefaultSessionBuilder setUserProperty(String key, String value) {
        if (value != null) {
            userProperties.put(key, value);
        } else {
            userProperties.remove(key);
        }
        return this;
    }

    @Override
    public DefaultSessionBuilder setConfigProperties(Map<?, ?> configProperties) {
        this.configProperties = copySafe(configProperties, Object.class);
        return this;
    }

    @Override
    public DefaultSessionBuilder setConfigProperty(String key, Object value) {
        if (value != null) {
            configProperties.put(key, value);
        } else {
            configProperties.remove(key);
        }
        return this;
    }

    @Override
    public DefaultSessionBuilder setMirrorSelector(MirrorSelector mirrorSelector) {
        this.mirrorSelector = mirrorSelector;
        if (this.mirrorSelector == null) {
            this.mirrorSelector = NULL_MIRROR_SELECTOR;
        }
        return this;
    }

    @Override
    public DefaultSessionBuilder setProxySelector(ProxySelector proxySelector) {
        this.proxySelector = proxySelector;
        if (this.proxySelector == null) {
            this.proxySelector = NULL_PROXY_SELECTOR;
        }
        return this;
    }

    @Override
    public DefaultSessionBuilder setAuthenticationSelector(AuthenticationSelector authenticationSelector) {
        this.authenticationSelector = authenticationSelector;
        if (this.authenticationSelector == null) {
            this.authenticationSelector = NULL_AUTHENTICATION_SELECTOR;
        }
        return this;
    }

    @Override
    public DefaultSessionBuilder setArtifactTypeRegistry(ArtifactTypeRegistry artifactTypeRegistry) {
        this.artifactTypeRegistry = artifactTypeRegistry;
        if (this.artifactTypeRegistry == null) {
            this.artifactTypeRegistry = NULL_ARTIFACT_TYPE_REGISTRY;
        }
        return this;
    }

    @Override
    public DefaultSessionBuilder setDependencyTraverser(DependencyTraverser dependencyTraverser) {
        this.dependencyTraverser = dependencyTraverser;
        return this;
    }

    @Override
    public DefaultSessionBuilder setDependencyManager(DependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
        return this;
    }

    @Override
    public DefaultSessionBuilder setDependencySelector(DependencySelector dependencySelector) {
        this.dependencySelector = dependencySelector;
        return this;
    }

    @Override
    public DefaultSessionBuilder setVersionFilter(VersionFilter versionFilter) {
        this.versionFilter = versionFilter;
        return this;
    }

    @Override
    public DefaultSessionBuilder setDependencyGraphTransformer(DependencyGraphTransformer dependencyGraphTransformer) {
        this.dependencyGraphTransformer = dependencyGraphTransformer;
        return this;
    }

    @Override
    public DefaultSessionBuilder setData(SessionData data) {
        this.data = data;
        if (this.data == null) {
            this.data = new DefaultSessionData();
        }
        return this;
    }

    @Override
    public DefaultSessionBuilder setCache(RepositoryCache cache) {
        this.cache = cache;
        return this;
    }

    @Override
    public SessionBuilder withLocalRepository(File basedir) {
        LocalRepository localRepository = new LocalRepository(basedir, "default");
        this.localRepositoryManager = repositorySystem.newLocalRepositoryManager(this, localRepository);
        return this;
    }

    @Override
    public SessionBuilder withRepositorySystemSession(RepositorySystemSession session) {
        requireNonNull(session, "repository system session cannot be null");
        setOffline(session.isOffline());
        setIgnoreArtifactDescriptorRepositories(session.isIgnoreArtifactDescriptorRepositories());
        setResolutionErrorPolicy(session.getResolutionErrorPolicy());
        setArtifactDescriptorPolicy(session.getArtifactDescriptorPolicy());
        setChecksumPolicy(session.getChecksumPolicy());
        setUpdatePolicy(session.getUpdatePolicy());
        setMetadataUpdatePolicy(session.getMetadataUpdatePolicy());
        setLocalRepositoryManager(session.getLocalRepositoryManager());
        setWorkspaceReader(session.getWorkspaceReader());
        setRepositoryListener(session.getRepositoryListener());
        setTransferListener(session.getTransferListener());
        setSystemProperties(session.getSystemProperties());
        setUserProperties(session.getUserProperties());
        setConfigProperties(session.getConfigProperties());
        setMirrorSelector(session.getMirrorSelector());
        setProxySelector(session.getProxySelector());
        setAuthenticationSelector(session.getAuthenticationSelector());
        setArtifactTypeRegistry(session.getArtifactTypeRegistry());
        setDependencyTraverser(session.getDependencyTraverser());
        setDependencyManager(session.getDependencyManager());
        setDependencySelector(session.getDependencySelector());
        setVersionFilter(session.getVersionFilter());
        setDependencyGraphTransformer(session.getDependencyGraphTransformer());
        setData(session.getData());
        setCache(session.getCache());
        return this;
    }

    @Override
    public CloseableSession build() {
        CloseableSession result = new DefaultCloseableSession(
                sessionId,
                closed,
                offline,
                ignoreArtifactDescriptorRepositories,
                resolutionErrorPolicy,
                artifactDescriptorPolicy,
                checksumPolicy,
                artifactUpdatePolicy,
                metadataUpdatePolicy,
                localRepositoryManager,
                workspaceReader,
                repositoryListener,
                transferListener,
                systemProperties,
                userProperties,
                configProperties,
                mirrorSelector,
                proxySelector,
                authenticationSelector,
                artifactTypeRegistry,
                dependencyTraverser,
                dependencyManager,
                dependencySelector,
                versionFilter,
                dependencyGraphTransformer,
                data,
                cache,
                repositorySystem,
                repositorySystemLifecycle);
        onCloseHandler.forEach(result::addOnSessionEndedHandler);
        return result;
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private static <T> Map<String, T> copySafe(Map<?, ?> table, Class<T> valueType) {
        Map<String, T> map;
        if (table == null || table.isEmpty()) {
            map = new HashMap<>();
        } else {
            map = new HashMap<>((int) (table.size() / 0.75f) + 1);
            for (Map.Entry<?, ?> entry : table.entrySet()) {
                Object key = entry.getKey();
                if (key instanceof String) {
                    Object value = entry.getValue();
                    if (valueType.isInstance(value)) {
                        map.put(key.toString(), valueType.cast(value));
                    }
                }
            }
        }
        return map;
    }
}
