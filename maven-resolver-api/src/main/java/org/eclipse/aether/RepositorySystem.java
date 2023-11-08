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
import java.util.Collection;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
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

/**
 * The main entry point to the repository system and its functionality. Note that obtaining a concrete implementation of
 * this interface (e.g. via dependency injection, service locator, etc.) is dependent on the application and its
 * specific needs, please consult the online documentation for examples and directions on booting the system.
 * <p>
 * When the repository system or the application integrating it is about to exit, invoke the {@link #shutdown()} to let
 * resolver system perform possible resource cleanups.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface RepositorySystem extends Closeable {

    /**
     * Expands a version range to a list of matching versions, in ascending order. For example, resolves "[3.8,4.0)" to
     * "3.8", "3.8.1", "3.8.2". Note that the returned list of versions is only dependent on the configured repositories
     * and their contents, the list is not processed by the {@link RepositorySystemSession#getVersionFilter() session's
     * version filter}.
     * <p>
     * The supplied request may also refer to a single concrete version rather than a version range. In this case
     * though, the result contains simply the (parsed) input version, regardless of the repositories and their contents.
     *
     * @param session The repository session, must not be {@code null}.
     * @param request The version range request, must not be {@code null}.
     * @return The version range result, never {@code null}.
     * @throws VersionRangeResolutionException If the requested range could not be parsed. Note that an empty range does
     *                                         not raise an exception.
     * @see #newResolutionRepositories(RepositorySystemSession, List)
     */
    VersionRangeResult resolveVersionRange(RepositorySystemSession session, VersionRangeRequest request)
            throws VersionRangeResolutionException;

    /**
     * Resolves an artifact's meta version (if any) to a concrete version. For example, resolves "1.0-SNAPSHOT" to
     * "1.0-20090208.132618-23".
     *
     * @param session The repository session, must not be {@code null}.
     * @param request The version request, must not be {@code null}.
     * @return The version result, never {@code null}.
     * @throws VersionResolutionException If the metaversion could not be resolved.
     * @see #newResolutionRepositories(RepositorySystemSession, List)
     */
    VersionResult resolveVersion(RepositorySystemSession session, VersionRequest request)
            throws VersionResolutionException;

    /**
     * Gets information about an artifact like its direct dependencies and potential relocations.
     *
     * @param session The repository session, must not be {@code null}.
     * @param request The descriptor request, must not be {@code null}.
     * @return The descriptor result, never {@code null}.
     * @throws ArtifactDescriptorException If the artifact descriptor could not be read.
     * @see RepositorySystemSession#getArtifactDescriptorPolicy()
     * @see #newResolutionRepositories(RepositorySystemSession, List)
     */
    ArtifactDescriptorResult readArtifactDescriptor(RepositorySystemSession session, ArtifactDescriptorRequest request)
            throws ArtifactDescriptorException;

    /**
     * Collects the transitive dependencies of an artifact and builds a dependency graph. Note that this operation is
     * only concerned about determining the coordinates of the transitive dependencies. To also resolve the actual
     * artifact files, use {@link #resolveDependencies(RepositorySystemSession, DependencyRequest)}.
     *
     * @param session The repository session, must not be {@code null}.
     * @param request The collection request, must not be {@code null}.
     * @return The collection result, never {@code null}.
     * @throws DependencyCollectionException If the dependency tree could not be built.
     * @see RepositorySystemSession#getDependencyTraverser()
     * @see RepositorySystemSession#getDependencyManager()
     * @see RepositorySystemSession#getDependencySelector()
     * @see RepositorySystemSession#getVersionFilter()
     * @see RepositorySystemSession#getDependencyGraphTransformer()
     * @see #newResolutionRepositories(RepositorySystemSession, List)
     */
    CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)
            throws DependencyCollectionException;

    /**
     * Collects and resolves the transitive dependencies of an artifact. This operation is essentially a combination of
     * {@link #collectDependencies(RepositorySystemSession, CollectRequest)} and
     * {@link #resolveArtifacts(RepositorySystemSession, Collection)}.
     *
     * @param session The repository session, must not be {@code null}.
     * @param request The dependency request, must not be {@code null}.
     * @return The dependency result, never {@code null}.
     * @throws DependencyResolutionException If the dependency tree could not be built or any dependency artifact could
     *                                       not be resolved.
     * @see #newResolutionRepositories(RepositorySystemSession, List)
     */
    DependencyResult resolveDependencies(RepositorySystemSession session, DependencyRequest request)
            throws DependencyResolutionException;

    /**
     * Flattens the provided graph as {@link DependencyNode} into a {@link List}{@code <DependencyNode>} according to session
     * configuration.
     *
     * @param session The repository session, must not be {@code null}.
     * @param root The dependency node root of the graph, must not be {@code null}.
     * @return The flattened list of dependency nodes, never {@code null}.
     * @since TBD
     */
    List<DependencyNode> flattenDependencyNodes(RepositorySystemSession session, DependencyNode root);

    /**
     * Resolves the path for an artifact. The artifact will be downloaded to the local repository if necessary. An
     * artifact that is already resolved will be skipped and is not re-resolved. In general, callers must not assume any
     * relationship between an artifact's resolved filename and its coordinates. Note that this method assumes that any
     * relocations have already been processed.
     *
     * @param session The repository session, must not be {@code null}.
     * @param request The resolution request, must not be {@code null}.
     * @return The resolution result, never {@code null}.
     * @throws ArtifactResolutionException If the artifact could not be resolved.
     * @see Artifact#getFile()
     * @see #newResolutionRepositories(RepositorySystemSession, List)
     */
    ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request)
            throws ArtifactResolutionException;

    /**
     * Resolves the paths for a collection of artifacts. Artifacts will be downloaded to the local repository if
     * necessary. Artifacts that are already resolved will be skipped and are not re-resolved. In general, callers must
     * not assume any relationship between an artifact's filename and its coordinates. Note that this method assumes
     * that any relocations have already been processed.
     *
     * @param session  The repository session, must not be {@code null}.
     * @param requests The resolution requests, must not be {@code null}.
     * @return The resolution results (in request order), never {@code null}.
     * @throws ArtifactResolutionException If any artifact could not be resolved.
     * @see Artifact#getFile()
     * @see #newResolutionRepositories(RepositorySystemSession, List)
     */
    List<ArtifactResult> resolveArtifacts(
            RepositorySystemSession session, Collection<? extends ArtifactRequest> requests)
            throws ArtifactResolutionException;

    /**
     * Resolves the paths for a collection of metadata. Metadata will be downloaded to the local repository if
     * necessary, e.g. because it hasn't been cached yet or the cache is deemed outdated.
     *
     * @param session  The repository session, must not be {@code null}.
     * @param requests The resolution requests, must not be {@code null}.
     * @return The resolution results (in request order), never {@code null}.
     * @see Metadata#getFile()
     * @see #newResolutionRepositories(RepositorySystemSession, List)
     */
    List<MetadataResult> resolveMetadata(
            RepositorySystemSession session, Collection<? extends MetadataRequest> requests);

    /**
     * Installs a collection of artifacts and their accompanying metadata to the local repository.
     *
     * @param session The repository session, must not be {@code null}.
     * @param request The installation request, must not be {@code null}.
     * @return The installation result, never {@code null}.
     * @throws InstallationException If any artifact/metadata from the request could not be installed.
     */
    InstallResult install(RepositorySystemSession session, InstallRequest request) throws InstallationException;

    /**
     * Uploads a collection of artifacts and their accompanying metadata to a remote repository.
     *
     * @param session The repository session, must not be {@code null}.
     * @param request The deployment request, must not be {@code null}.
     * @return The deployment result, never {@code null}.
     * @throws DeploymentException If any artifact/metadata from the request could not be deployed.
     * @see #newDeploymentRepository(RepositorySystemSession, RemoteRepository)
     */
    DeployResult deploy(RepositorySystemSession session, DeployRequest request) throws DeploymentException;

    /**
     * Creates a new manager for the specified local repository. If the specified local repository has no type, the
     * default local repository type of the system will be used. <em>Note:</em> It is expected that this method
     * invocation is one of the last steps of setting up a new session, in particular any configuration properties
     * should have been set already.
     *
     * @param session         The repository system session from which to configure the manager, must not be
     *                        {@code null}.
     * @param localRepository The local repository to create a manager for, must not be {@code null}.
     * @return The local repository manager, never {@code null}.
     * @throws IllegalArgumentException If the specified repository type is not recognized or no base directory is
     *                                  given.
     */
    LocalRepositoryManager newLocalRepositoryManager(RepositorySystemSession session, LocalRepository localRepository);

    /**
     * Creates a new synchronization context.
     *
     * @param session The repository session during which the context will be used, must not be {@code null}.
     * @param shared  A flag indicating whether access to the artifacts/metadata associated with the new context can be
     *                shared among concurrent readers or whether access needs to be exclusive to the calling thread.
     * @return The synchronization context, never {@code null}.
     */
    SyncContext newSyncContext(RepositorySystemSession session, boolean shared);

    /**
     * Forms remote repositories suitable for artifact resolution by applying the session's authentication selector and
     * similar network configuration to the given repository prototypes. As noted for
     * {@link RepositorySystemSession#getAuthenticationSelector()} etc. the remote repositories passed to e.g.
     * {@link #resolveArtifact(RepositorySystemSession, ArtifactRequest) resolveArtifact()} are used as is and expected
     * to already carry any required authentication or proxy configuration. This method can be used to apply the
     * authentication/proxy configuration from a session to a bare repository definition to obtain the complete
     * repository definition for use in the resolution request.
     *
     * @param session      The repository system session from which to configure the repositories, must not be
     *                     {@code null}.
     * @param repositories The repository prototypes from which to derive the resolution repositories, must not be
     *                     {@code null} or contain {@code null} elements.
     * @return The resolution repositories, never {@code null}. Note that there is generally no 1:1 relationship of the
     * obtained repositories to the original inputs due to mirror selection potentially aggregating multiple
     * repositories.
     * @see #newDeploymentRepository(RepositorySystemSession, RemoteRepository)
     */
    List<RemoteRepository> newResolutionRepositories(
            RepositorySystemSession session, List<RemoteRepository> repositories);

    /**
     * Forms a remote repository suitable for artifact deployment by applying the session's authentication selector and
     * similar network configuration to the given repository prototype. As noted for
     * {@link RepositorySystemSession#getAuthenticationSelector()} etc. the remote repository passed to
     * {@link #deploy(RepositorySystemSession, DeployRequest) deploy()} is used as is and expected to already carry any
     * required authentication or proxy configuration. This method can be used to apply the authentication/proxy
     * configuration from a session to a bare repository definition to obtain the complete repository definition for use
     * in the deployment request.
     *
     * @param session    The repository system session from which to configure the repository, must not be {@code null}.
     * @param repository The repository prototype from which to derive the deployment repository, must not be
     *                   {@code null}.
     * @return The deployment repository, never {@code null}.
     * @see #newResolutionRepositories(RepositorySystemSession, List)
     */
    RemoteRepository newDeploymentRepository(RepositorySystemSession session, RemoteRepository repository);

    /**
     * Registers an "on repository system end" handler, executed after repository system is shut down.
     *
     * @param handler The handler, must not be {@code null}.
     * @since 1.9.0
     */
    void addOnSystemEndedHandler(Runnable handler);

    /**
     * Creates a brand-new session builder instance that produces "top level" (root) session. Top level sessions are
     * associated with its creator {@link RepositorySystem} instance, and may be used only with that given instance and
     * only within the lifespan of it, and after use should be closed.
     *
     * @since TBD
     */
    RepositorySystemSession.SessionBuilder createSessionBuilder();

    /**
     * Signals to repository system to shut down. Shut down instance is not usable anymore.
     * <p>
     * Repository system may perform some resource cleanup, if applicable. Not using this method may cause leaks or
     * unclean shutdown of some subsystem.
     * <p>
     * When shutdown happens, all the registered on-close handlers will be invoked (even if some throws), and at end
     * of operation a {@link MultiRuntimeException} may be thrown, signaling that some handler(s) failed. This exception
     * may be ignored, is at the discretion of caller.
     * <p>
     * Note: this method actually just calls {@link #close()}.
     *
     * @since 1.9.0
     * @deprecated Use {@link #close()} instead.
     */
    @Deprecated
    void shutdown();

    /**
     * Signals to repository system to shut down. Shut down instance is not usable anymore.
     * <p>
     * Repository system may perform some resource cleanup, if applicable. Not using this method may cause leaks or
     * unclean shutdown of some subsystem.
     * <p>
     * When shutdown happens, all the registered on-close handlers will be invoked (even if some throws), and at end
     * of operation a {@link MultiRuntimeException} may be thrown, signaling that some handler(s) failed. This exception
     * may be ignored, is at the discretion of caller.
     *
     * @since TBD
     */
    @Override
    void close();
}
