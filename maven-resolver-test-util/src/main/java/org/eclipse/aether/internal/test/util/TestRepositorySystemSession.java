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
package org.eclipse.aether.internal.test.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.aether.*;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.*;
import org.eclipse.aether.repository.*;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.transfer.TransferListener;

import static java.util.Objects.requireNonNull;

/**
 * Test utility to create root sessions.
 */
public final class TestRepositorySystemSession implements RepositorySystemSession {
    private boolean readOnly;

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

    private Map<String, String> systemProperties;

    private Map<String, String> systemPropertiesView;

    private Map<String, String> userProperties;

    private Map<String, String> userPropertiesView;

    private Map<String, Object> configProperties;

    private Map<String, Object> configPropertiesView;

    private MirrorSelector mirrorSelector;

    private ProxySelector proxySelector;

    private AuthenticationSelector authenticationSelector;

    private ArtifactTypeRegistry artifactTypeRegistry;

    private DependencyTraverser dependencyTraverser;

    private DependencyManager dependencyManager;

    private DependencySelector dependencySelector;

    private VersionFilter versionFilter;

    private DependencyGraphTransformer dependencyGraphTransformer;

    private SessionData data;

    private RepositoryCache cache;

    private final Function<Runnable, Boolean> onSessionEndedRegistrar;

    public TestRepositorySystemSession(Consumer<Runnable> onSessionCloseConsumer) {
        systemProperties = new HashMap<>();
        systemPropertiesView = Collections.unmodifiableMap(systemProperties);
        userProperties = new HashMap<>();
        userPropertiesView = Collections.unmodifiableMap(userProperties);
        configProperties = new HashMap<>();
        configPropertiesView = Collections.unmodifiableMap(configProperties);
        mirrorSelector = NullMirrorSelector.INSTANCE;
        proxySelector = NullProxySelector.INSTANCE;
        authenticationSelector = NullAuthenticationSelector.INSTANCE;
        artifactTypeRegistry = NullArtifactTypeRegistry.INSTANCE;
        data = new DefaultSessionData();
        onSessionEndedRegistrar = h -> {
            if (onSessionCloseConsumer != null) {
                onSessionCloseConsumer.accept(h);
                return true;
            } else {
                return false;
            }
        };
    }

    public TestRepositorySystemSession(RepositorySystemSession session) {
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
        this.onSessionEndedRegistrar = session::addOnSessionEndedHandler;
    }

    @Override
    public boolean isOffline() {
        return offline;
    }

    /**
     * Controls whether the repository system operates in offline mode and avoids/refuses any access to remote
     * repositories.
     *
     * @param offline {@code true} if the repository system is in offline mode, {@code false} otherwise.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setOffline(boolean offline) {
        verifyStateForMutation();
        this.offline = offline;
        return this;
    }

    @Override
    public boolean isIgnoreArtifactDescriptorRepositories() {
        return ignoreArtifactDescriptorRepositories;
    }

    /**
     * Controls whether repositories declared in artifact descriptors should be ignored during transitive dependency
     * collection. If enabled, only the repositories originally provided with the collect request will be considered.
     *
     * @param ignoreArtifactDescriptorRepositories {@code true} to ignore additional repositories from artifact
     *                                             descriptors, {@code false} to merge those with the originally
     *                                             specified repositories.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setIgnoreArtifactDescriptorRepositories(
            boolean ignoreArtifactDescriptorRepositories) {
        verifyStateForMutation();
        this.ignoreArtifactDescriptorRepositories = ignoreArtifactDescriptorRepositories;
        return this;
    }

    @Override
    public ResolutionErrorPolicy getResolutionErrorPolicy() {
        return resolutionErrorPolicy;
    }

    /**
     * Sets the policy which controls whether resolutions errors from remote repositories should be cached.
     *
     * @param resolutionErrorPolicy The resolution error policy for this session, may be {@code null} if resolution
     *                              errors should generally not be cached.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setResolutionErrorPolicy(ResolutionErrorPolicy resolutionErrorPolicy) {
        verifyStateForMutation();
        this.resolutionErrorPolicy = resolutionErrorPolicy;
        return this;
    }

    @Override
    public ArtifactDescriptorPolicy getArtifactDescriptorPolicy() {
        return artifactDescriptorPolicy;
    }

    /**
     * Sets the policy which controls how errors related to reading artifact descriptors should be handled.
     *
     * @param artifactDescriptorPolicy The descriptor error policy for this session, may be {@code null} if descriptor
     *                                 errors should generally not be tolerated.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setArtifactDescriptorPolicy(ArtifactDescriptorPolicy artifactDescriptorPolicy) {
        verifyStateForMutation();
        this.artifactDescriptorPolicy = artifactDescriptorPolicy;
        return this;
    }

    @Override
    public String getChecksumPolicy() {
        return checksumPolicy;
    }

    /**
     * Sets the global checksum policy. If set, the global checksum policy overrides the checksum policies of the remote
     * repositories being used for resolution.
     *
     * @param checksumPolicy The global checksum policy, may be {@code null}/empty to apply the per-repository policies.
     * @return This session for chaining, never {@code null}.
     * @see RepositoryPolicy#CHECKSUM_POLICY_FAIL
     * @see RepositoryPolicy#CHECKSUM_POLICY_IGNORE
     * @see RepositoryPolicy#CHECKSUM_POLICY_WARN
     */
    public TestRepositorySystemSession setChecksumPolicy(String checksumPolicy) {
        verifyStateForMutation();
        this.checksumPolicy = checksumPolicy;
        return this;
    }

    @Override
    public String getUpdatePolicy() {
        return getArtifactUpdatePolicy();
    }

    /**
     * Sets the global update policy. If set, the global update policy overrides the update policies of the remote
     * repositories being used for resolution.
     * <p>
     * This method is meant for code that does not want to distinguish between artifact and metadata policies.
     * Note: applications should either use get/set updatePolicy (this method and
     * {@link RepositorySystemSession#getUpdatePolicy()}) or also distinguish between artifact and
     * metadata update policies (and use other methods), but <em>should not mix the two!</em>
     *
     * @param updatePolicy The global update policy, may be {@code null}/empty to apply the per-repository policies.
     * @return This session for chaining, never {@code null}.
     * @see RepositoryPolicy#UPDATE_POLICY_ALWAYS
     * @see RepositoryPolicy#UPDATE_POLICY_DAILY
     * @see RepositoryPolicy#UPDATE_POLICY_NEVER
     * @see #setArtifactUpdatePolicy(String)
     * @see #setMetadataUpdatePolicy(String)
     */
    public TestRepositorySystemSession setUpdatePolicy(String updatePolicy) {
        verifyStateForMutation();
        setArtifactUpdatePolicy(updatePolicy);
        setMetadataUpdatePolicy(updatePolicy);
        return this;
    }

    @Override
    public String getArtifactUpdatePolicy() {
        return artifactUpdatePolicy;
    }

    /**
     * Sets the global artifact update policy. If set, the global update policy overrides the artifact update policies
     * of the remote repositories being used for resolution.
     *
     * @param artifactUpdatePolicy The global update policy, may be {@code null}/empty to apply the per-repository policies.
     * @return This session for chaining, never {@code null}.
     * @see RepositoryPolicy#UPDATE_POLICY_ALWAYS
     * @see RepositoryPolicy#UPDATE_POLICY_DAILY
     * @see RepositoryPolicy#UPDATE_POLICY_NEVER
     * @since 2.0.0
     */
    public TestRepositorySystemSession setArtifactUpdatePolicy(String artifactUpdatePolicy) {
        verifyStateForMutation();
        this.artifactUpdatePolicy = artifactUpdatePolicy;
        return this;
    }

    @Override
    public String getMetadataUpdatePolicy() {
        return metadataUpdatePolicy;
    }

    /**
     * Sets the global metadata update policy. If set, the global update policy overrides the metadata update policies
     * of the remote repositories being used for resolution.
     *
     * @param metadataUpdatePolicy The global update policy, may be {@code null}/empty to apply the per-repository policies.
     * @return This session for chaining, never {@code null}.
     * @see RepositoryPolicy#UPDATE_POLICY_ALWAYS
     * @see RepositoryPolicy#UPDATE_POLICY_DAILY
     * @see RepositoryPolicy#UPDATE_POLICY_NEVER
     * @since 2.0.0
     */
    public TestRepositorySystemSession setMetadataUpdatePolicy(String metadataUpdatePolicy) {
        verifyStateForMutation();
        this.metadataUpdatePolicy = metadataUpdatePolicy;
        return this;
    }

    @Override
    public LocalRepository getLocalRepository() {
        LocalRepositoryManager lrm = getLocalRepositoryManager();
        return (lrm != null) ? lrm.getRepository() : null;
    }

    @Override
    public LocalRepositoryManager getLocalRepositoryManager() {
        return localRepositoryManager;
    }

    /**
     * Sets the local repository manager used during this session. <em>Note:</em> Eventually, a valid session must have
     * a local repository manager set.
     *
     * @param localRepositoryManager The local repository manager used during this session, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setLocalRepositoryManager(LocalRepositoryManager localRepositoryManager) {
        verifyStateForMutation();
        this.localRepositoryManager = localRepositoryManager;
        return this;
    }

    @Override
    public WorkspaceReader getWorkspaceReader() {
        return workspaceReader;
    }

    /**
     * Sets the workspace reader used during this session. If set, the workspace reader will usually be consulted first
     * to resolve artifacts.
     *
     * @param workspaceReader The workspace reader for this session, may be {@code null} if none.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setWorkspaceReader(WorkspaceReader workspaceReader) {
        verifyStateForMutation();
        this.workspaceReader = workspaceReader;
        return this;
    }

    @Override
    public RepositoryListener getRepositoryListener() {
        return repositoryListener;
    }

    /**
     * Sets the listener being notified of actions in the repository system.
     *
     * @param repositoryListener The repository listener, may be {@code null} if none.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setRepositoryListener(RepositoryListener repositoryListener) {
        verifyStateForMutation();
        this.repositoryListener = repositoryListener;
        return this;
    }

    @Override
    public TransferListener getTransferListener() {
        return transferListener;
    }

    /**
     * Sets the listener being notified of uploads/downloads by the repository system.
     *
     * @param transferListener The transfer listener, may be {@code null} if none.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setTransferListener(TransferListener transferListener) {
        verifyStateForMutation();
        this.transferListener = transferListener;
        return this;
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private <T> Map<String, T> copySafe(Map<?, ?> table, Class<T> valueType) {
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

    @Override
    public Map<String, String> getSystemProperties() {
        return systemPropertiesView;
    }

    /**
     * Sets the system properties to use, e.g. for processing of artifact descriptors. System properties are usually
     * collected from the runtime environment like {@link System#getProperties()} and environment variables.
     * <p>
     * <em>Note:</em> System properties are of type {@code Map<String, String>} and any key-value pair in the input map
     * that doesn't match this type will be silently ignored.
     *
     * @param systemProperties The system properties, may be {@code null} or empty if none.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setSystemProperties(Map<?, ?> systemProperties) {
        verifyStateForMutation();
        this.systemProperties = copySafe(systemProperties, String.class);
        systemPropertiesView = Collections.unmodifiableMap(this.systemProperties);
        return this;
    }

    /**
     * Sets the specified system property.
     *
     * @param key   The property key, must not be {@code null}.
     * @param value The property value, may be {@code null} to remove/unset the property.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setSystemProperty(String key, String value) {
        verifyStateForMutation();
        if (value != null) {
            systemProperties.put(key, value);
        } else {
            systemProperties.remove(key);
        }
        return this;
    }

    @Override
    public Map<String, String> getUserProperties() {
        return userPropertiesView;
    }

    /**
     * Sets the user properties to use, e.g. for processing of artifact descriptors. User properties are similar to
     * system properties but are set on the discretion of the user and hence are considered of higher priority than
     * system properties in case of conflicts.
     * <p>
     * <em>Note:</em> User properties are of type {@code Map<String, String>} and any key-value pair in the input map
     * that doesn't match this type will be silently ignored.
     *
     * @param userProperties The user properties, may be {@code null} or empty if none.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setUserProperties(Map<?, ?> userProperties) {
        verifyStateForMutation();
        this.userProperties = copySafe(userProperties, String.class);
        userPropertiesView = Collections.unmodifiableMap(this.userProperties);
        return this;
    }

    /**
     * Sets the specified user property.
     *
     * @param key   The property key, must not be {@code null}.
     * @param value The property value, may be {@code null} to remove/unset the property.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setUserProperty(String key, String value) {
        verifyStateForMutation();
        if (value != null) {
            userProperties.put(key, value);
        } else {
            userProperties.remove(key);
        }
        return this;
    }

    @Override
    public Map<String, Object> getConfigProperties() {
        return configPropertiesView;
    }

    /**
     * Sets the configuration properties used to tweak internal aspects of the repository system (e.g. thread pooling,
     * connector-specific behavior, etc.).
     * <p>
     * <em>Note:</em> Configuration properties are of type {@code Map<String, Object>} and any key-value pair in the
     * input map that doesn't match this type will be silently ignored.
     *
     * @param configProperties The configuration properties, may be {@code null} or empty if none.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setConfigProperties(Map<?, ?> configProperties) {
        verifyStateForMutation();
        this.configProperties = copySafe(configProperties, Object.class);
        configPropertiesView = Collections.unmodifiableMap(this.configProperties);
        return this;
    }

    /**
     * Sets the specified configuration property.
     *
     * @param key   The property key, must not be {@code null}.
     * @param value The property value, may be {@code null} to remove/unset the property.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setConfigProperty(String key, Object value) {
        verifyStateForMutation();
        if (value != null) {
            configProperties.put(key, value);
        } else {
            configProperties.remove(key);
        }
        return this;
    }

    @Override
    public MirrorSelector getMirrorSelector() {
        return mirrorSelector;
    }

    /**
     * Sets the mirror selector to use for repositories discovered in artifact descriptors. Note that this selector is
     * not used for remote repositories which are passed as request parameters to the repository system, those
     * repositories are supposed to denote the effective repositories.
     *
     * @param mirrorSelector The mirror selector to use, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setMirrorSelector(MirrorSelector mirrorSelector) {
        verifyStateForMutation();
        this.mirrorSelector = mirrorSelector;
        if (this.mirrorSelector == null) {
            this.mirrorSelector = NullMirrorSelector.INSTANCE;
        }
        return this;
    }

    @Override
    public ProxySelector getProxySelector() {
        return proxySelector;
    }

    /**
     * Sets the proxy selector to use for repositories discovered in artifact descriptors. Note that this selector is
     * not used for remote repositories which are passed as request parameters to the repository system, those
     * repositories are supposed to have their proxy (if any) already set.
     *
     * @param proxySelector The proxy selector to use, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     * @see RemoteRepository#getProxy()
     */
    public TestRepositorySystemSession setProxySelector(ProxySelector proxySelector) {
        verifyStateForMutation();
        this.proxySelector = proxySelector;
        if (this.proxySelector == null) {
            this.proxySelector = NullProxySelector.INSTANCE;
        }
        return this;
    }

    @Override
    public AuthenticationSelector getAuthenticationSelector() {
        return authenticationSelector;
    }

    /**
     * Sets the authentication selector to use for repositories discovered in artifact descriptors. Note that this
     * selector is not used for remote repositories which are passed as request parameters to the repository system,
     * those repositories are supposed to have their authentication (if any) already set.
     *
     * @param authenticationSelector The authentication selector to use, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     * @see RemoteRepository#getAuthentication()
     */
    public TestRepositorySystemSession setAuthenticationSelector(AuthenticationSelector authenticationSelector) {
        verifyStateForMutation();
        this.authenticationSelector = authenticationSelector;
        if (this.authenticationSelector == null) {
            this.authenticationSelector = NullAuthenticationSelector.INSTANCE;
        }
        return this;
    }

    @Override
    public ArtifactTypeRegistry getArtifactTypeRegistry() {
        return artifactTypeRegistry;
    }

    /**
     * Sets the registry of artifact types recognized by this session.
     *
     * @param artifactTypeRegistry The artifact type registry, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setArtifactTypeRegistry(ArtifactTypeRegistry artifactTypeRegistry) {
        verifyStateForMutation();
        this.artifactTypeRegistry = artifactTypeRegistry;
        if (this.artifactTypeRegistry == null) {
            this.artifactTypeRegistry = NullArtifactTypeRegistry.INSTANCE;
        }
        return this;
    }

    @Override
    public DependencyTraverser getDependencyTraverser() {
        return dependencyTraverser;
    }

    /**
     * Sets the dependency traverser to use for building dependency graphs.
     *
     * @param dependencyTraverser The dependency traverser to use for building dependency graphs, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setDependencyTraverser(DependencyTraverser dependencyTraverser) {
        verifyStateForMutation();
        this.dependencyTraverser = dependencyTraverser;
        return this;
    }

    @Override
    public DependencyManager getDependencyManager() {
        return dependencyManager;
    }

    /**
     * Sets the dependency manager to use for building dependency graphs.
     *
     * @param dependencyManager The dependency manager to use for building dependency graphs, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setDependencyManager(DependencyManager dependencyManager) {
        verifyStateForMutation();
        this.dependencyManager = dependencyManager;
        return this;
    }

    @Override
    public DependencySelector getDependencySelector() {
        return dependencySelector;
    }

    /**
     * Sets the dependency selector to use for building dependency graphs.
     *
     * @param dependencySelector The dependency selector to use for building dependency graphs, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setDependencySelector(DependencySelector dependencySelector) {
        verifyStateForMutation();
        this.dependencySelector = dependencySelector;
        return this;
    }

    @Override
    public VersionFilter getVersionFilter() {
        return versionFilter;
    }

    /**
     * Sets the version filter to use for building dependency graphs.
     *
     * @param versionFilter The version filter to use for building dependency graphs, may be {@code null} to not filter
     *                      versions.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setVersionFilter(VersionFilter versionFilter) {
        verifyStateForMutation();
        this.versionFilter = versionFilter;
        return this;
    }

    @Override
    public DependencyGraphTransformer getDependencyGraphTransformer() {
        return dependencyGraphTransformer;
    }

    /**
     * Sets the dependency graph transformer to use for building dependency graphs.
     *
     * @param dependencyGraphTransformer The dependency graph transformer to use for building dependency graphs, may be
     *                                   {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setDependencyGraphTransformer(
            DependencyGraphTransformer dependencyGraphTransformer) {
        verifyStateForMutation();
        this.dependencyGraphTransformer = dependencyGraphTransformer;
        return this;
    }

    @Override
    public SessionData getData() {
        return data;
    }

    /**
     * Sets the custom data associated with this session.
     *
     * @param data The session data, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setData(SessionData data) {
        verifyStateForMutation();
        this.data = data;
        if (this.data == null) {
            this.data = new DefaultSessionData();
        }
        return this;
    }

    @Override
    public RepositoryCache getCache() {
        return cache;
    }

    /**
     * Sets the cache the repository system may use to save data for future reuse during the session.
     *
     * @param cache The repository cache, may be {@code null} if none.
     * @return This session for chaining, never {@code null}.
     */
    public TestRepositorySystemSession setCache(RepositoryCache cache) {
        verifyStateForMutation();
        this.cache = cache;
        return this;
    }

    /**
     * Registers onSessionEnded handler, if able to.
     *
     * @param handler The handler to register
     * @return Return {@code true} if registration was possible, otherwise {@code false}.
     */
    @Override
    public boolean addOnSessionEndedHandler(Runnable handler) {
        return onSessionEndedRegistrar.apply(handler);
    }

    /**
     * Marks this session as read-only such that any future attempts to call its mutators will fail with an exception.
     * Marking an already read-only session as read-only has no effect. The session's data and cache remain writable
     * though.
     */
    public void setReadOnly() {
        readOnly = true;
    }

    /**
     * Verifies this instance state for mutation operations: mutated instance must not be read-only or closed.
     */
    private void verifyStateForMutation() {
        if (readOnly) {
            throw new IllegalStateException("repository system session is read-only");
        }
    }

    static class NullProxySelector implements ProxySelector {

        public static final ProxySelector INSTANCE = new NullProxySelector();

        public Proxy getProxy(RemoteRepository repository) {
            requireNonNull(repository, "repository cannot be null");
            return repository.getProxy();
        }
    }

    static class NullMirrorSelector implements MirrorSelector {

        public static final MirrorSelector INSTANCE = new NullMirrorSelector();

        public RemoteRepository getMirror(RemoteRepository repository) {
            requireNonNull(repository, "repository cannot be null");
            return null;
        }
    }

    static class NullAuthenticationSelector implements AuthenticationSelector {

        public static final AuthenticationSelector INSTANCE = new NullAuthenticationSelector();

        public Authentication getAuthentication(RemoteRepository repository) {
            requireNonNull(repository, "repository cannot be null");
            return repository.getAuthentication();
        }
    }

    static final class NullArtifactTypeRegistry implements ArtifactTypeRegistry {

        public static final ArtifactTypeRegistry INSTANCE = new NullArtifactTypeRegistry();

        public ArtifactType get(String typeId) {
            return null;
        }
    }
}