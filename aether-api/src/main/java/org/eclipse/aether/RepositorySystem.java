/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether;

import java.util.Collection;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
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
 * The main entry point to the repository system.
 */
public interface RepositorySystem
{

    /**
     * Expands a version range to a list of matching versions, in ascending order. For example, resolves "[3.8,4.0)" to
     * ["3.8", "3.8.1", "3.8.2"].
     * 
     * @param session The repository session, must not be {@code null}.
     * @param request The version range request, must not be {@code null}.
     * @return The version range result, never {@code null}.
     * @throws VersionRangeResolutionException If the requested range could not be parsed. Note that an empty range does
     *             not raise an exception.
     */
    VersionRangeResult resolveVersionRange( RepositorySystemSession session, VersionRangeRequest request )
        throws VersionRangeResolutionException;

    /**
     * Resolves an artifact's meta version (if any) to a concrete version. For example, resolves "1.0-SNAPSHOT" to
     * "1.0-20090208.132618-23".
     * 
     * @param session The repository session, must not be {@code null}.
     * @param request The version request, must not be {@code null}.
     * @return The version result, never {@code null}.
     * @throws VersionResolutionException If the metaversion could not be resolved.
     */
    VersionResult resolveVersion( RepositorySystemSession session, VersionRequest request )
        throws VersionResolutionException;

    /**
     * Gets information about an artifact like its direct dependencies and potential relocations.
     * 
     * @param session The repository session, must not be {@code null}.
     * @param request The descriptor request, must not be {@code null}.
     * @return The descriptor result, never {@code null}.
     * @throws ArtifactDescriptorException If the artifact descriptor could not be read.
     * @see RepositorySystemSession#isIgnoreInvalidArtifactDescriptor()
     * @see RepositorySystemSession#isIgnoreMissingArtifactDescriptor()
     */
    ArtifactDescriptorResult readArtifactDescriptor( RepositorySystemSession session, ArtifactDescriptorRequest request )
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
     * @see RepositorySystemSession#getDependencyGraphTransformer()
     */
    CollectResult collectDependencies( RepositorySystemSession session, CollectRequest request )
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
     *             not be resolved.
     */
    DependencyResult resolveDependencies( RepositorySystemSession session, DependencyRequest request )
        throws DependencyResolutionException;

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
     */
    ArtifactResult resolveArtifact( RepositorySystemSession session, ArtifactRequest request )
        throws ArtifactResolutionException;

    /**
     * Resolves the paths for a collection of artifacts. Artifacts will be downloaded to the local repository if
     * necessary. Artifacts that are already resolved will be skipped and are not re-resolved. In general, callers must
     * not assume any relationship between an artifact's filename and its coordinates. Note that this method assumes
     * that any relocations have already been processed.
     * 
     * @param session The repository session, must not be {@code null}.
     * @param requests The resolution requests, must not be {@code null}.
     * @return The resolution results (in request order), never {@code null}.
     * @throws ArtifactResolutionException If any artifact could not be resolved.
     * @see Artifact#getFile()
     */
    List<ArtifactResult> resolveArtifacts( RepositorySystemSession session,
                                           Collection<? extends ArtifactRequest> requests )
        throws ArtifactResolutionException;

    /**
     * Resolves the paths for a collection of metadata. Metadata will be downloaded to the local repository if
     * necessary, e.g. because it hasn't been cached yet or the cache is deemed outdated.
     * 
     * @param session The repository session, must not be {@code null}.
     * @param requests The resolution requests, must not be {@code null}.
     * @return The resolution results (in request order), never {@code null}.
     * @see Metadata#getFile()
     */
    List<MetadataResult> resolveMetadata( RepositorySystemSession session,
                                          Collection<? extends MetadataRequest> requests );

    /**
     * Installs a collection of artifacts and their accompanying metadata to the local repository.
     * 
     * @param session The repository session, must not be {@code null}.
     * @param request The installation request, must not be {@code null}.
     * @return The installation result, never {@code null}.
     * @throws InstallationException If any artifact/metadata from the request could not be installed.
     */
    InstallResult install( RepositorySystemSession session, InstallRequest request )
        throws InstallationException;

    /**
     * Uploads a collection of artifacts and their accompanying metadata to a remote repository.
     * 
     * @param session The repository session, must not be {@code null}.
     * @param request The deployment request, must not be {@code null}.
     * @return The deployment result, never {@code null}.
     * @throws DeploymentException If any artifact/metadata from the request could not be deployed.
     */
    DeployResult deploy( RepositorySystemSession session, DeployRequest request )
        throws DeploymentException;

    /**
     * Creates a new manager for the specified local repository. If the specified local repository has no type, the
     * default repository type will be used.
     * 
     * @param localRepository The local repository to create a manager for, must not be {@code null}.
     * @return The local repository manager, never {@code null}.
     * @throws IllegalArgumentException If the specified repository type is not recognized or no base directory is
     *             given.
     */
    LocalRepositoryManager newLocalRepositoryManager( LocalRepository localRepository );

    /**
     * Creates a new synchronization context.
     * 
     * @param session The repository session during which the context will be used, must not be {@code null}.
     * @param shared A flag indicating whether access to the artifacts/metadata associated with the new context can be
     *            shared among concurrent readers or whether access needs to be exclusive to the calling thread.
     * @return The synchronization context, never {@code null}.
     */
    SyncContext newSyncContext( RepositorySystemSession session, boolean shared );

}
