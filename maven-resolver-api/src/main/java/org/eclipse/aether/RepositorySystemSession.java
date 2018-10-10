package org.eclipse.aether;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transform.FileTransformerManager;

/**
 * Defines settings and components that control the repository system. Once initialized, the session object itself is
 * supposed to be immutable and hence can safely be shared across an entire application and any concurrent threads
 * reading it. Components that wish to tweak some aspects of an existing session should use the copy constructor of
 * {@link DefaultRepositorySystemSession} and its mutators to derive a custom session.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface RepositorySystemSession
{

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
     * Gets the global update policy. If set, the global update policy overrides the update policies of the remote
     * repositories being used for resolution.
     * 
     * @return The global update policy or {@code null}/empty if not set and the per-repository policies apply.
     * @see RepositoryPolicy#UPDATE_POLICY_ALWAYS
     * @see RepositoryPolicy#UPDATE_POLICY_DAILY
     * @see RepositoryPolicy#UPDATE_POLICY_NEVER
     */
    String getUpdatePolicy();

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
     * Get the file transformer manager
     * 
     * @return the manager, never {@code null}
     */
    FileTransformerManager getFileTransformerManager();

}
