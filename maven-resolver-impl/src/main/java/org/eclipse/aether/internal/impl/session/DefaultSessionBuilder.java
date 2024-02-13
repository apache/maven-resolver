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

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

import org.eclipse.aether.DefaultSessionData;
import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.RepositorySystemSession.SessionBuilder;
import org.eclipse.aether.SessionData;
import org.eclipse.aether.SystemScopeHandler;
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
import static java.util.stream.Collectors.toList;

/**
 * A default implementation of session builder. Is not immutable nor thread-safe.
 */
public final class DefaultSessionBuilder implements SessionBuilder {
    private static final MirrorSelector NULL_MIRROR_SELECTOR = r -> null;

    private static final ProxySelector NULL_PROXY_SELECTOR = RemoteRepository::getProxy;

    private static final AuthenticationSelector NULL_AUTHENTICATION_SELECTOR = RemoteRepository::getAuthentication;

    private static final ArtifactTypeRegistry NULL_ARTIFACT_TYPE_REGISTRY = t -> null;

    private static final Supplier<SessionData> DEFAULT_SESSION_DATA_SUPPLIER = DefaultSessionData::new;

    private static final Supplier<RepositoryCache> DEFAULT_REPOSITORY_CACHE_SUPPLIER = () -> null;

    private final RepositorySystem repositorySystem;

    private final RepositorySystemLifecycle repositorySystemLifecycle;

    private final Supplier<String> sessionIdSupplier;

    private boolean offline;

    private boolean ignoreArtifactDescriptorRepositories;

    private ResolutionErrorPolicy resolutionErrorPolicy;

    private ArtifactDescriptorPolicy artifactDescriptorPolicy;

    private String checksumPolicy;

    private String artifactUpdatePolicy;

    private String metadataUpdatePolicy;

    private LocalRepositoryManager localRepositoryManager;

    private Collection<LocalRepository> localRepositories;

    private WorkspaceReader workspaceReader;

    private final ArrayList<RepositoryListener> repositoryListener = new ArrayList<>();

    private final ArrayList<TransferListener> transferListener = new ArrayList<>();

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

    private Supplier<SessionData> sessionDataSupplier = DEFAULT_SESSION_DATA_SUPPLIER;

    private Supplier<RepositoryCache> repositoryCacheSupplier = DEFAULT_REPOSITORY_CACHE_SUPPLIER;

    private SystemScopeHandler systemScopeHandler = SystemScopeHandler.LEGACY;

    private final ArrayList<Runnable> onSessionCloseHandlers = new ArrayList<>();

    /**
     * Constructor for "top level" builders.
     */
    public DefaultSessionBuilder(
            RepositorySystem repositorySystem,
            RepositorySystemLifecycle repositorySystemLifecycle,
            Supplier<String> sessionIdSupplier) {
        this.repositorySystem = requireNonNull(repositorySystem);
        this.repositorySystemLifecycle = requireNonNull(repositorySystemLifecycle);
        this.sessionIdSupplier = requireNonNull(sessionIdSupplier);
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
        this.repositoryListener.clear();
        if (repositoryListener != null) {
            this.repositoryListener.add(repositoryListener);
        }
        return this;
    }

    @Override
    public DefaultSessionBuilder setTransferListener(TransferListener transferListener) {
        this.transferListener.clear();
        if (transferListener != null) {
            this.transferListener.add(transferListener);
        }
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
        return setSessionDataSupplier(() -> data);
    }

    @Override
    public DefaultSessionBuilder setSessionDataSupplier(Supplier<SessionData> dataSupplier) {
        requireNonNull(dataSupplier, "null dataSupplier");
        this.sessionDataSupplier = dataSupplier;
        return this;
    }

    @Override
    public DefaultSessionBuilder setCache(RepositoryCache cache) {
        return setRepositoryCacheSupplier(() -> cache);
    }

    @Override
    public DefaultSessionBuilder setSystemScopeHandler(SystemScopeHandler systemScopeHandler) {
        requireNonNull(systemScopeHandler, "null systemScopeHandler");
        this.systemScopeHandler = systemScopeHandler;
        return this;
    }

    @Override
    public SessionBuilder addOnSessionEndedHandler(Runnable handler) {
        requireNonNull(handler, "null handler");
        onSessionCloseHandlers.add(handler);
        return this;
    }

    @Override
    public DefaultSessionBuilder setRepositoryCacheSupplier(Supplier<RepositoryCache> cacheSupplier) {
        requireNonNull(cacheSupplier, "null cacheSupplier");
        this.repositoryCacheSupplier = cacheSupplier;
        return this;
    }

    @Override
    public SessionBuilder withLocalRepositoryBaseDirectories(Path... baseDirectories) {
        return withLocalRepositoryBaseDirectories(Arrays.asList(baseDirectories));
    }

    @Override
    public SessionBuilder withLocalRepositoryBaseDirectories(Collection<Path> baseDirectories) {
        requireNonNull(baseDirectories, "null baseDirectories");
        return withLocalRepositories(
                baseDirectories.stream().map(LocalRepository::new).collect(toList()));
    }

    @Override
    public SessionBuilder withLocalRepositories(LocalRepository... localRepositories) {
        return withLocalRepositories(Arrays.asList(localRepositories));
    }

    @Override
    public SessionBuilder withLocalRepositories(Collection<LocalRepository> localRepositories) {
        requireNonNull(localRepositories, "null localRepositories");
        this.localRepositories = localRepositories;
        return this;
    }

    @Override
    public SessionBuilder withRepositoryListener(RepositoryListener... repositoryListeners) {
        return withRepositoryListener(Arrays.asList(repositoryListeners));
    }

    @Override
    public SessionBuilder withRepositoryListener(Collection<RepositoryListener> repositoryListeners) {
        this.repositoryListener.addAll(repositoryListeners);
        return this;
    }

    @Override
    public SessionBuilder withTransferListener(TransferListener... transferListeners) {
        return withTransferListener(Arrays.asList(transferListeners));
    }

    @Override
    public SessionBuilder withTransferListener(Collection<TransferListener> transferListeners) {
        this.transferListener.addAll(transferListeners);
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
        setSystemScopeHandler(session.getSystemScopeHandler());
        return this;
    }

    @Override
    public CloseableSession build() {
        return new DefaultCloseableSession(
                sessionIdSupplier.get(),
                offline,
                ignoreArtifactDescriptorRepositories,
                resolutionErrorPolicy,
                artifactDescriptorPolicy,
                checksumPolicy,
                artifactUpdatePolicy,
                metadataUpdatePolicy,
                localRepositoryManager,
                localRepositories,
                workspaceReader,
                repositoryListener,
                transferListener,
                copySafe(systemProperties, String.class),
                copySafe(userProperties, String.class),
                copySafe(configProperties, Object.class),
                mirrorSelector,
                proxySelector,
                authenticationSelector,
                artifactTypeRegistry,
                dependencyTraverser,
                dependencyManager,
                dependencySelector,
                versionFilter,
                dependencyGraphTransformer,
                sessionDataSupplier.get(),
                repositoryCacheSupplier.get(),
                systemScopeHandler,
                onSessionCloseHandlers,
                repositorySystem,
                repositorySystemLifecycle);
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
