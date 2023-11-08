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
package org.eclipse.aether;

import java.io.Closeable;
import java.io.File;
import java.util.Map;

import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.transfer.TransferListener;

/**
 * Defines settings and components that control the repository system. Once initialized, the session object itself is
 * supposed to be immutable and hence can safely be shared across an entire application and any concurrent threads
 * reading it. Components that wish to tweak some aspects of an existing session should use the copy constructor of
 * {@link DefaultRepositorySystemSession} and its mutators to derive a custom session.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface RepositorySystemSession {

    /**
     * Immutable session that is closeable, can have onClose handlers registered and should be handled as a resource.
     * These session instances can be created with {@link SessionBuilder}.
     *
     * @since TBD
     */
    interface CloseableRepositorySystemSession extends RepositorySystemSession, Closeable {
        /**
         * Returns the ID of this closeable session instance. Each closeable session has different ID, unique within
         * repository system, that they were created for.
         *
         * @return The session ID that is never {@code null}.
         */
        String sessionId();

        /**
         * Copies this session into a pre-populated builder, effectively making a mutable copy of itself, builder builds
         * <em>same session</em>. Important: this session <em>remains unchanged</em> upon return of this method but
         * this session and returned builder created session will have <em>same identity</em>. It is up to client code,
         * will it close only the original (this) session or new session, or both. Important is, that at least one of
         * the sessions must be closed, and consequence is that once either one is closed, the other session is closed
         * as well.
         * <p>
         * This pattern should be applied in "filter" like constructs, when code needs to alter the incoming session and
         * subsequently pass it downstream.
         */
        SessionBuilder copy();

        /**
         * Closes the session. The session should be closed by its creator. A closed session should not be used anymore.
         * This method may be invoked multiple times, but close will act only once (first time).
         */
        @Override
        void close();
    }

    /**
     * Builder for building {@link CloseableRepositorySystemSession} instances. Builder instances can be created with
     * {@link RepositorySystem#createSessionBuilder()} method, and built sessions must be handled as resources
     * (closed once done with them).
     *
     * @since TBD
     */
    interface SessionBuilder {
        /**
         * Controls whether the repository system operates in offline mode and avoids/refuses any access to remote
         * repositories.
         *
         * @param offline {@code true} if the repository system is in offline mode, {@code false} otherwise.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setOffline(boolean offline);

        /**
         * Controls whether repositories declared in artifact descriptors should be ignored during transitive dependency
         * collection. If enabled, only the repositories originally provided with the collect request will be considered.
         *
         * @param ignoreArtifactDescriptorRepositories {@code true} to ignore additional repositories from artifact
         *                                             descriptors, {@code false} to merge those with the originally
         *                                             specified repositories.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setIgnoreArtifactDescriptorRepositories(boolean ignoreArtifactDescriptorRepositories);

        /**
         * Sets the policy which controls whether resolutions errors from remote repositories should be cached.
         *
         * @param resolutionErrorPolicy The resolution error policy for this session, may be {@code null} if resolution
         *                              errors should generally not be cached.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setResolutionErrorPolicy(ResolutionErrorPolicy resolutionErrorPolicy);

        /**
         * Sets the policy which controls how errors related to reading artifact descriptors should be handled.
         *
         * @param artifactDescriptorPolicy The descriptor error policy for this session, may be {@code null} if descriptor
         *                                 errors should generally not be tolerated.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setArtifactDescriptorPolicy(ArtifactDescriptorPolicy artifactDescriptorPolicy);

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
        SessionBuilder setChecksumPolicy(String checksumPolicy);

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
        SessionBuilder setUpdatePolicy(String updatePolicy);

        /**
         * Sets the global artifact update policy. If set, the global update policy overrides the artifact update policies
         * of the remote repositories being used for resolution.
         *
         * @param artifactUpdatePolicy The global update policy, may be {@code null}/empty to apply the per-repository policies.
         * @return This session for chaining, never {@code null}.
         * @see RepositoryPolicy#UPDATE_POLICY_ALWAYS
         * @see RepositoryPolicy#UPDATE_POLICY_DAILY
         * @see RepositoryPolicy#UPDATE_POLICY_NEVER
         * @since TBD
         */
        SessionBuilder setArtifactUpdatePolicy(String artifactUpdatePolicy);

        /**
         * Sets the global metadata update policy. If set, the global update policy overrides the metadata update policies
         * of the remote repositories being used for resolution.
         *
         * @param metadataUpdatePolicy The global update policy, may be {@code null}/empty to apply the per-repository policies.
         * @return This session for chaining, never {@code null}.
         * @see RepositoryPolicy#UPDATE_POLICY_ALWAYS
         * @see RepositoryPolicy#UPDATE_POLICY_DAILY
         * @see RepositoryPolicy#UPDATE_POLICY_NEVER
         * @since TBD
         */
        SessionBuilder setMetadataUpdatePolicy(String metadataUpdatePolicy);

        /**
         * Sets the local repository manager used during this session. <em>Note:</em> Eventually, a valid session must have
         * a local repository manager set.
         *
         * @param localRepositoryManager The local repository manager used during this session, may be {@code null}.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setLocalRepositoryManager(LocalRepositoryManager localRepositoryManager);

        /**
         * Sets the workspace reader used during this session. If set, the workspace reader will usually be consulted first
         * to resolve artifacts.
         *
         * @param workspaceReader The workspace reader for this session, may be {@code null} if none.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setWorkspaceReader(WorkspaceReader workspaceReader);

        /**
         * Sets the listener being notified of actions in the repository system.
         *
         * @param repositoryListener The repository listener, may be {@code null} if none.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setRepositoryListener(RepositoryListener repositoryListener);

        /**
         * Sets the listener being notified of uploads/downloads by the repository system.
         *
         * @param transferListener The transfer listener, may be {@code null} if none.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setTransferListener(TransferListener transferListener);

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
        SessionBuilder setSystemProperties(Map<?, ?> systemProperties);

        /**
         * Sets the specified system property.
         *
         * @param key   The property key, must not be {@code null}.
         * @param value The property value, may be {@code null} to remove/unset the property.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setSystemProperty(String key, String value);

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
        SessionBuilder setUserProperties(Map<?, ?> userProperties);

        /**
         * Sets the specified user property.
         *
         * @param key   The property key, must not be {@code null}.
         * @param value The property value, may be {@code null} to remove/unset the property.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setUserProperty(String key, String value);

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
        SessionBuilder setConfigProperties(Map<?, ?> configProperties);

        /**
         * Sets the specified configuration property.
         *
         * @param key   The property key, must not be {@code null}.
         * @param value The property value, may be {@code null} to remove/unset the property.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setConfigProperty(String key, Object value);

        /**
         * Sets the mirror selector to use for repositories discovered in artifact descriptors. Note that this selector is
         * not used for remote repositories which are passed as request parameters to the repository system, those
         * repositories are supposed to denote the effective repositories.
         *
         * @param mirrorSelector The mirror selector to use, may be {@code null}.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setMirrorSelector(MirrorSelector mirrorSelector);

        /**
         * Sets the proxy selector to use for repositories discovered in artifact descriptors. Note that this selector is
         * not used for remote repositories which are passed as request parameters to the repository system, those
         * repositories are supposed to have their proxy (if any) already set.
         *
         * @param proxySelector The proxy selector to use, may be {@code null}.
         * @return This session for chaining, never {@code null}.
         * @see RemoteRepository#getProxy()
         */
        SessionBuilder setProxySelector(ProxySelector proxySelector);

        /**
         * Sets the authentication selector to use for repositories discovered in artifact descriptors. Note that this
         * selector is not used for remote repositories which are passed as request parameters to the repository system,
         * those repositories are supposed to have their authentication (if any) already set.
         *
         * @param authenticationSelector The authentication selector to use, may be {@code null}.
         * @return This session for chaining, never {@code null}.
         * @see RemoteRepository#getAuthentication()
         */
        SessionBuilder setAuthenticationSelector(AuthenticationSelector authenticationSelector);

        /**
         * Sets the registry of artifact types recognized by this session.
         *
         * @param artifactTypeRegistry The artifact type registry, may be {@code null}.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setArtifactTypeRegistry(ArtifactTypeRegistry artifactTypeRegistry);

        /**
         * Sets the dependency traverser to use for building dependency graphs.
         *
         * @param dependencyTraverser The dependency traverser to use for building dependency graphs, may be {@code null}.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setDependencyTraverser(DependencyTraverser dependencyTraverser);

        /**
         * Sets the dependency manager to use for building dependency graphs.
         *
         * @param dependencyManager The dependency manager to use for building dependency graphs, may be {@code null}.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setDependencyManager(DependencyManager dependencyManager);

        /**
         * Sets the dependency selector to use for building dependency graphs.
         *
         * @param dependencySelector The dependency selector to use for building dependency graphs, may be {@code null}.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setDependencySelector(DependencySelector dependencySelector);

        /**
         * Sets the version filter to use for building dependency graphs.
         *
         * @param versionFilter The version filter to use for building dependency graphs, may be {@code null} to not filter
         *                      versions.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setVersionFilter(VersionFilter versionFilter);

        /**
         * Sets the dependency graph transformer to use for building dependency graphs.
         *
         * @param dependencyGraphTransformer The dependency graph transformer to use for building dependency graphs, may be
         *                                   {@code null}.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setDependencyGraphTransformer(DependencyGraphTransformer dependencyGraphTransformer);

        /**
         * Sets the custom data associated with this session.
         *
         * @param data The session data, may be {@code null}.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setData(SessionData data);

        /**
         * Sets the cache the repository system may use to save data for future reuse during the session.
         *
         * @param cache The repository cache, may be {@code null} if none.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder setCache(RepositoryCache cache);

        /**
         * Shortcut method to set up local repository manager.
         *
         * @param basedir The local repository base directory, may be {@code null} if none.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder withLocalRepository(File basedir);

        /**
         * Shortcut method to shallow-copy passed in session into current builder.
         *
         * @param session The session to shallow-copy from.
         * @return This session for chaining, never {@code null}.
         */
        SessionBuilder withRepositorySystemSession(RepositorySystemSession session);

        /**
         * Creates a session instance.
         */
        CloseableRepositorySystemSession build();
    }

    /**
     * Indicates whether the repository system operates in offline mode and avoids/refuses any access to remote
     * repositories.
     *
     * @return {@code true} if the repository system is in offline mode, {@code false} otherwise.
     */
    boolean isOffline();

    /**
     * Indicates whether repositories declared in artifact descriptors should be ignored during transitive dependency
     * collection. If enabled, only the repositories originally provided with the collect request will be considered.
     *
     * @return {@code true} if additional repositories from artifact descriptors are ignored, {@code false} to merge
     *         those with the originally specified repositories.
     */
    boolean isIgnoreArtifactDescriptorRepositories();

    /**
     * Gets the policy which controls whether resolutions errors from remote repositories should be cached.
     *
     * @return The resolution error policy for this session or {@code null} if resolution errors should generally not be
     *         cached.
     */
    ResolutionErrorPolicy getResolutionErrorPolicy();

    /**
     * Gets the policy which controls how errors related to reading artifact descriptors should be handled.
     *
     * @return The descriptor error policy for this session or {@code null} if descriptor errors should generally not be
     *         tolerated.
     */
    ArtifactDescriptorPolicy getArtifactDescriptorPolicy();

    /**
     * Gets the global checksum policy. If set, the global checksum policy overrides the checksum policies of the remote
     * repositories being used for resolution.
     *
     * @return The global checksum policy or {@code null}/empty if not set and the per-repository policies apply.
     * @see RepositoryPolicy#CHECKSUM_POLICY_FAIL
     * @see RepositoryPolicy#CHECKSUM_POLICY_IGNORE
     * @see RepositoryPolicy#CHECKSUM_POLICY_WARN
     */
    String getChecksumPolicy();

    /**
     * Gets the global update policy, or {@code null} if not set.
     * <p>
     * This method is meant for code that does not want to distinguish between artifact and metadata policies.
     * Note: applications should either use get/set updatePolicy (this method and
     * {@link DefaultRepositorySystemSession#setUpdatePolicy(String)}) or also distinguish between artifact and
     * metadata update policies (and use other methods), but <em>should not mix the two!</em>
     *
     * @see #getArtifactUpdatePolicy()
     * @see #getMetadataUpdatePolicy()
     */
    String getUpdatePolicy();

    /**
     * Gets the global artifact update policy. If set, the global update policy overrides the update policies of the
     * remote repositories being used for resolution.
     *
     * @return The global update policy or {@code null}/empty if not set and the per-repository policies apply.
     * @see RepositoryPolicy#UPDATE_POLICY_ALWAYS
     * @see RepositoryPolicy#UPDATE_POLICY_DAILY
     * @see RepositoryPolicy#UPDATE_POLICY_NEVER
     * @since TBD
     */
    String getArtifactUpdatePolicy();

    /**
     * Gets the global metadata update policy. If set, the global update policy overrides the update policies of the remote
     * repositories being used for resolution.
     *
     * @return The global update policy or {@code null}/empty if not set and the per-repository policies apply.
     * @see RepositoryPolicy#UPDATE_POLICY_ALWAYS
     * @see RepositoryPolicy#UPDATE_POLICY_DAILY
     * @see RepositoryPolicy#UPDATE_POLICY_NEVER
     * @since TBD
     */
    String getMetadataUpdatePolicy();

    /**
     * Gets the local repository used during this session. This is a convenience method for
     * {@link LocalRepositoryManager#getRepository()}.
     *
     * @return The local repository being during this session, never {@code null}.
     */
    LocalRepository getLocalRepository();

    /**
     * Gets the local repository manager used during this session.
     *
     * @return The local repository manager used during this session, never {@code null}.
     */
    LocalRepositoryManager getLocalRepositoryManager();

    /**
     * Gets the workspace reader used during this session. If set, the workspace reader will usually be consulted first
     * to resolve artifacts.
     *
     * @return The workspace reader for this session or {@code null} if none.
     */
    WorkspaceReader getWorkspaceReader();

    /**
     * Gets the listener being notified of actions in the repository system.
     *
     * @return The repository listener or {@code null} if none.
     */
    RepositoryListener getRepositoryListener();

    /**
     * Gets the listener being notified of uploads/downloads by the repository system.
     *
     * @return The transfer listener or {@code null} if none.
     */
    TransferListener getTransferListener();

    /**
     * Gets the system properties to use, e.g. for processing of artifact descriptors. System properties are usually
     * collected from the runtime environment like {@link System#getProperties()} and environment variables.
     *
     * @return The (read-only) system properties, never {@code null}.
     */
    Map<String, String> getSystemProperties();

    /**
     * Gets the user properties to use, e.g. for processing of artifact descriptors. User properties are similar to
     * system properties but are set on the discretion of the user and hence are considered of higher priority than
     * system properties.
     *
     * @return The (read-only) user properties, never {@code null}.
     */
    Map<String, String> getUserProperties();

    /**
     * Gets the configuration properties used to tweak internal aspects of the repository system (e.g. thread pooling,
     * connector-specific behavior, etc.)
     *
     * @return The (read-only) configuration properties, never {@code null}.
     * @see ConfigurationProperties
     */
    Map<String, Object> getConfigProperties();

    /**
     * Gets the mirror selector to use for repositories discovered in artifact descriptors. Note that this selector is
     * not used for remote repositories which are passed as request parameters to the repository system, those
     * repositories are supposed to denote the effective repositories.
     *
     * @return The mirror selector to use, never {@code null}.
     * @see RepositorySystem#newResolutionRepositories(RepositorySystemSession, java.util.List)
     */
    MirrorSelector getMirrorSelector();

    /**
     * Gets the proxy selector to use for repositories discovered in artifact descriptors. Note that this selector is
     * not used for remote repositories which are passed as request parameters to the repository system, those
     * repositories are supposed to have their proxy (if any) already set.
     *
     * @return The proxy selector to use, never {@code null}.
     * @see org.eclipse.aether.repository.RemoteRepository#getProxy()
     * @see RepositorySystem#newResolutionRepositories(RepositorySystemSession, java.util.List)
     */
    ProxySelector getProxySelector();

    /**
     * Gets the authentication selector to use for repositories discovered in artifact descriptors. Note that this
     * selector is not used for remote repositories which are passed as request parameters to the repository system,
     * those repositories are supposed to have their authentication (if any) already set.
     *
     * @return The authentication selector to use, never {@code null}.
     * @see org.eclipse.aether.repository.RemoteRepository#getAuthentication()
     * @see RepositorySystem#newResolutionRepositories(RepositorySystemSession, java.util.List)
     */
    AuthenticationSelector getAuthenticationSelector();

    /**
     * Gets the registry of artifact types recognized by this session, for instance when processing artifact
     * descriptors.
     *
     * @return The artifact type registry, never {@code null}.
     */
    ArtifactTypeRegistry getArtifactTypeRegistry();

    /**
     * Gets the dependency traverser to use for building dependency graphs.
     *
     * @return The dependency traverser to use for building dependency graphs or {@code null} if dependencies are
     *         unconditionally traversed.
     */
    DependencyTraverser getDependencyTraverser();

    /**
     * Gets the dependency manager to use for building dependency graphs.
     *
     * @return The dependency manager to use for building dependency graphs or {@code null} if dependency management is
     *         not performed.
     */
    DependencyManager getDependencyManager();

    /**
     * Gets the dependency selector to use for building dependency graphs.
     *
     * @return The dependency selector to use for building dependency graphs or {@code null} if dependencies are
     *         unconditionally included.
     */
    DependencySelector getDependencySelector();

    /**
     * Gets the version filter to use for building dependency graphs.
     *
     * @return The version filter to use for building dependency graphs or {@code null} if versions aren't filtered.
     */
    VersionFilter getVersionFilter();

    /**
     * Gets the dependency graph transformer to use for building dependency graphs.
     *
     * @return The dependency graph transformer to use for building dependency graphs or {@code null} if none.
     */
    DependencyGraphTransformer getDependencyGraphTransformer();

    /**
     * Gets the custom data associated with this session.
     *
     * @return The session data, never {@code null}.
     */
    SessionData getData();

    /**
     * Gets the cache the repository system may use to save data for future reuse during the session.
     *
     * @return The repository cache or {@code null} if none.
     */
    RepositoryCache getCache();

    /**
     * Registers a handler to execute when this session closed.
     * <p>
     * Note: Resolver 1.x sessions will not be able to register handlers. Migrate to Resolver 2.x way of handling
     * sessions to make full use of new features. New features (like HTTP/2 transport) depend on this functionality.
     * While they will function with Resolver 1.x sessions, they may produce resource leaks.
     *
     * @param handler the handler, never {@code null}.
     * @return {@code true} if handler successfully registered, {@code false} otherwise.
     * @since TBD
     */
    boolean addOnSessionEndedHandler(Runnable handler);
}
