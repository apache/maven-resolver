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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.impl.RemoteRepositoryFilterManager;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.impl.UpdateCheck;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.scope.SystemDependencyScope;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.spi.resolution.ArtifactResolverPostProcessor;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.transfer.ArtifactFilteredOutException;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 *
 */
@Singleton
@Named
public class DefaultArtifactResolver implements ArtifactResolver {

    public static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_AETHER + "artifactResolver.";

    /**
     * Configuration to enable "snapshot normalization", downloaded snapshots from remote with timestamped file names
     * will have file names converted back to baseVersion. It replaces the timestamped snapshot file name with a
     * filename containing the SNAPSHOT qualifier only. This only affects resolving/retrieving artifacts but not
     * uploading those.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_SNAPSHOT_NORMALIZATION}
     */
    public static final String CONFIG_PROP_SNAPSHOT_NORMALIZATION = CONFIG_PROPS_PREFIX + "snapshotNormalization";

    public static final boolean DEFAULT_SNAPSHOT_NORMALIZATION = true;

    /**
     * Configuration to enable "interoperability" with Simple LRM, but this breaks RRF feature, hence this configuration
     * is IGNORED when RRF is used, and is warmly recommended to leave it disabled even if no RRF is being used.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_SIMPLE_LRM_INTEROP}
     */
    public static final String CONFIG_PROP_SIMPLE_LRM_INTEROP = CONFIG_PROPS_PREFIX + "simpleLrmInterop";

    public static final boolean DEFAULT_SIMPLE_LRM_INTEROP = false;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultArtifactResolver.class);

    private final PathProcessor pathProcessor;

    private final RepositoryEventDispatcher repositoryEventDispatcher;

    private final VersionResolver versionResolver;

    private final UpdateCheckManager updateCheckManager;

    private final RepositoryConnectorProvider repositoryConnectorProvider;

    private final RemoteRepositoryManager remoteRepositoryManager;

    private final SyncContextFactory syncContextFactory;

    private final OfflineController offlineController;

    private final Map<String, ArtifactResolverPostProcessor> artifactResolverPostProcessors;

    private final RemoteRepositoryFilterManager remoteRepositoryFilterManager;

    @SuppressWarnings("checkstyle:parameternumber")
    @Inject
    public DefaultArtifactResolver(
            PathProcessor pathProcessor,
            RepositoryEventDispatcher repositoryEventDispatcher,
            VersionResolver versionResolver,
            UpdateCheckManager updateCheckManager,
            RepositoryConnectorProvider repositoryConnectorProvider,
            RemoteRepositoryManager remoteRepositoryManager,
            SyncContextFactory syncContextFactory,
            OfflineController offlineController,
            Map<String, ArtifactResolverPostProcessor> artifactResolverPostProcessors,
            RemoteRepositoryFilterManager remoteRepositoryFilterManager) {
        this.pathProcessor = requireNonNull(pathProcessor, "path processor cannot be null");
        this.repositoryEventDispatcher =
                requireNonNull(repositoryEventDispatcher, "repository event dispatcher cannot be null");
        this.versionResolver = requireNonNull(versionResolver, "version resolver cannot be null");
        this.updateCheckManager = requireNonNull(updateCheckManager, "update check manager cannot be null");
        this.repositoryConnectorProvider =
                requireNonNull(repositoryConnectorProvider, "repository connector provider cannot be null");
        this.remoteRepositoryManager =
                requireNonNull(remoteRepositoryManager, "remote repository provider cannot be null");
        this.syncContextFactory = requireNonNull(syncContextFactory, "sync context factory cannot be null");
        this.offlineController = requireNonNull(offlineController, "offline controller cannot be null");
        this.artifactResolverPostProcessors =
                requireNonNull(artifactResolverPostProcessors, "artifact resolver post-processors cannot be null");
        this.remoteRepositoryFilterManager =
                requireNonNull(remoteRepositoryFilterManager, "remote repository filter manager cannot be null");
    }

    @Override
    public ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request)
            throws ArtifactResolutionException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");

        return resolveArtifacts(session, Collections.singleton(request)).get(0);
    }

    @Override
    public List<ArtifactResult> resolveArtifacts(
            RepositorySystemSession session, Collection<? extends ArtifactRequest> requests)
            throws ArtifactResolutionException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(requests, "requests cannot be null");
        try (SyncContext shared = syncContextFactory.newInstance(session, true);
                SyncContext exclusive = syncContextFactory.newInstance(session, false)) {
            Collection<Artifact> artifacts = new ArrayList<>(requests.size());
            SystemDependencyScope systemDependencyScope = session.getSystemDependencyScope();
            for (ArtifactRequest request : requests) {
                if (systemDependencyScope != null
                        && systemDependencyScope.getSystemPath(request.getArtifact()) != null) {
                    continue;
                }
                artifacts.add(request.getArtifact());
            }

            return resolve(shared, exclusive, artifacts, session, requests);
        }
    }

    @SuppressWarnings("checkstyle:methodlength")
    private List<ArtifactResult> resolve(
            SyncContext shared,
            SyncContext exclusive,
            Collection<Artifact> subjects,
            RepositorySystemSession session,
            Collection<? extends ArtifactRequest> requests)
            throws ArtifactResolutionException {
        SystemDependencyScope systemDependencyScope = session.getSystemDependencyScope();
        SyncContext current = shared;
        try {
            while (true) {
                current.acquire(subjects, null);

                boolean failures = false;
                final List<ArtifactResult> results = new ArrayList<>(requests.size());
                final boolean simpleLrmInterop =
                        ConfigUtils.getBoolean(session, DEFAULT_SIMPLE_LRM_INTEROP, CONFIG_PROP_SIMPLE_LRM_INTEROP);
                final LocalRepositoryManager lrm = session.getLocalRepositoryManager();
                final WorkspaceReader workspace = session.getWorkspaceReader();
                final List<ResolutionGroup> groups = new ArrayList<>();
                // filter != null: means "filtering applied", if null no filtering applied (behave as before)
                final RemoteRepositoryFilter filter = remoteRepositoryFilterManager.getRemoteRepositoryFilter(session);

                for (ArtifactRequest request : requests) {
                    RequestTrace trace = RequestTrace.newChild(request.getTrace(), request);

                    ArtifactResult result = new ArtifactResult(request);
                    results.add(result);

                    Artifact artifact = request.getArtifact();

                    if (current == shared) {
                        artifactResolving(session, trace, artifact);
                    }

                    String localPath =
                            systemDependencyScope != null ? systemDependencyScope.getSystemPath(artifact) : null;
                    if (localPath != null) {
                        // unhosted artifact, just validate file
                        Path path = Paths.get(localPath);
                        if (!Files.isRegularFile(path)) {
                            failures = true;
                            result.addException(
                                    ArtifactResult.NO_REPOSITORY, new ArtifactNotFoundException(artifact, localPath));
                        } else {
                            artifact = artifact.setPath(path);
                            result.setArtifact(artifact);
                            artifactResolved(session, trace, artifact, null, result.getExceptions());
                        }
                        continue;
                    }

                    List<RemoteRepository> remoteRepositories = request.getRepositories();
                    List<RemoteRepository> filteredRemoteRepositories = new ArrayList<>(remoteRepositories);
                    if (filter != null) {
                        for (RemoteRepository repository : remoteRepositories) {
                            RemoteRepositoryFilter.Result filterResult = filter.acceptArtifact(repository, artifact);
                            if (!filterResult.isAccepted()) {
                                result.addException(
                                        repository,
                                        new ArtifactFilteredOutException(
                                                artifact, repository, filterResult.reasoning()));
                                filteredRemoteRepositories.remove(repository);
                            }
                        }
                    }

                    VersionResult versionResult;
                    try {
                        VersionRequest versionRequest =
                                new VersionRequest(artifact, filteredRemoteRepositories, request.getRequestContext());
                        versionRequest.setTrace(trace);
                        versionResult = versionResolver.resolveVersion(session, versionRequest);
                    } catch (VersionResolutionException e) {
                        if (filteredRemoteRepositories.isEmpty()) {
                            result.addException(lrm.getRepository(), e);
                        } else {
                            filteredRemoteRepositories.forEach(r -> result.addException(r, e));
                        }
                        continue;
                    }

                    artifact = artifact.setVersion(versionResult.getVersion());

                    if (versionResult.getRepository() != null) {
                        if (versionResult.getRepository() instanceof RemoteRepository) {
                            filteredRemoteRepositories =
                                    Collections.singletonList((RemoteRepository) versionResult.getRepository());
                        } else {
                            filteredRemoteRepositories = Collections.emptyList();
                        }
                    }

                    if (workspace != null) {
                        Path path = workspace.findArtifactPath(artifact);
                        if (path != null) {
                            artifact = artifact.setPath(path);
                            result.setArtifact(artifact);
                            result.setRepository(workspace.getRepository());
                            artifactResolved(session, trace, artifact, result.getRepository(), null);
                            continue;
                        }
                    }

                    LocalArtifactResult local = lrm.find(
                            session,
                            new LocalArtifactRequest(
                                    artifact, filteredRemoteRepositories, request.getRequestContext()));
                    result.setLocalArtifactResult(local);
                    boolean found = (filter != null && local.isAvailable())
                            || (filter == null && isLocallyInstalled(local, versionResult));
                    // with filtering: availability drives the logic
                    // without filtering: simply presence of file drives the logic
                    // "interop" logic with simple LRM leads to RRF breakage: hence is ignored when filtering in effect
                    if (found) {
                        if (local.getRepository() != null) {
                            result.setRepository(local.getRepository());
                        } else {
                            result.setRepository(lrm.getRepository());
                        }

                        try {
                            artifact = artifact.setPath(getPath(session, artifact, local.getPath()));
                            result.setArtifact(artifact);
                            artifactResolved(session, trace, artifact, result.getRepository(), null);
                        } catch (ArtifactTransferException e) {
                            result.addException(lrm.getRepository(), e);
                        }
                        if (filter == null && simpleLrmInterop && !local.isAvailable()) {
                            /*
                             * NOTE: Interop with simple local repository: An artifact installed by a simple local repo
                             * manager will not show up in the repository tracking file of the enhanced local repository.
                             * If however the maven-metadata-local.xml tells us the artifact was installed locally, we
                             * sync the repository tracking file.
                             */
                            lrm.add(session, new LocalArtifactRegistration(artifact));
                        }

                        continue;
                    }

                    if (local.getPath() != null) {
                        LOGGER.info(
                                "Artifact {} is present in the local repository, but cached from a remote repository ID that is unavailable in current build context, verifying that is downloadable from {}",
                                artifact,
                                remoteRepositories);
                    }

                    LOGGER.debug("Resolving artifact {} from {}", artifact, remoteRepositories);
                    AtomicBoolean resolved = new AtomicBoolean(false);
                    Iterator<ResolutionGroup> groupIt = groups.iterator();
                    for (RemoteRepository repo : filteredRemoteRepositories) {
                        if (!repo.getPolicy(artifact.isSnapshot()).isEnabled()) {
                            continue;
                        }

                        try {
                            Utils.checkOffline(session, offlineController, repo);
                        } catch (RepositoryOfflineException e) {
                            Exception exception = new ArtifactNotFoundException(
                                    artifact,
                                    repo,
                                    "Cannot access " + repo.getId() + " ("
                                            + repo.getUrl() + ") in offline mode and the artifact " + artifact
                                            + " has not been downloaded from it before.",
                                    e);
                            result.addException(repo, exception);
                            continue;
                        }

                        ResolutionGroup group = null;
                        while (groupIt.hasNext()) {
                            ResolutionGroup t = groupIt.next();
                            if (t.matches(repo)) {
                                group = t;
                                break;
                            }
                        }
                        if (group == null) {
                            group = new ResolutionGroup(repo);
                            groups.add(group);
                            groupIt = Collections.emptyIterator();
                        }
                        group.items.add(new ResolutionItem(trace, artifact, resolved, result, local, repo));
                    }
                }

                if (!groups.isEmpty() && current == shared) {
                    current.close();
                    current = exclusive;
                    continue;
                }

                for (ResolutionGroup group : groups) {
                    performDownloads(session, group);
                }

                for (ArtifactResolverPostProcessor artifactResolverPostProcessor :
                        artifactResolverPostProcessors.values()) {
                    artifactResolverPostProcessor.postProcess(session, results);
                }

                for (ArtifactResult result : results) {
                    ArtifactRequest request = result.getRequest();

                    Artifact artifact = result.getArtifact();
                    if (artifact == null || artifact.getPath() == null) {
                        failures = true;
                        if (result.getExceptions().isEmpty()) {
                            Exception exception =
                                    new ArtifactNotFoundException(request.getArtifact(), (RemoteRepository) null);
                            result.addException(result.getRepository(), exception);
                        }
                        RequestTrace trace = RequestTrace.newChild(request.getTrace(), request);
                        artifactResolved(session, trace, request.getArtifact(), null, result.getExceptions());
                    }
                }

                if (failures) {
                    throw new ArtifactResolutionException(results);
                }

                return results;
            }
        } finally {
            current.close();
        }
    }

    private boolean isLocallyInstalled(LocalArtifactResult lar, VersionResult vr) {
        if (lar.isAvailable()) {
            return true;
        }
        if (lar.getPath() != null) {
            // resolution of version range found locally installed artifact
            if (vr.getRepository() instanceof LocalRepository) {
                // resolution of (snapshot) version found locally installed artifact
                return true;
            } else {
                return vr.getRepository() == null
                        && lar.getRequest().getRepositories().isEmpty();
            }
        }
        return false;
    }

    private Path getPath(RepositorySystemSession session, Artifact artifact, Path path)
            throws ArtifactTransferException {
        if (artifact.isSnapshot()
                && !artifact.getVersion().equals(artifact.getBaseVersion())
                && ConfigUtils.getBoolean(
                        session, DEFAULT_SNAPSHOT_NORMALIZATION, CONFIG_PROP_SNAPSHOT_NORMALIZATION)) {
            String name = path.getFileName().toString().replace(artifact.getVersion(), artifact.getBaseVersion());
            Path dst = path.getParent().resolve(name);

            try {
                long pathLastModified = pathProcessor.lastModified(path, 0L);
                boolean copy = pathProcessor.size(dst, 0L) != pathProcessor.size(path, 0L)
                        || pathProcessor.lastModified(dst, 0L) != pathLastModified;
                if (copy) {
                    pathProcessor.copyWithTimestamp(path, dst);
                }
            } catch (IOException e) {
                throw new ArtifactTransferException(artifact, null, e);
            }

            path = dst;
        }

        return path;
    }

    private void performDownloads(RepositorySystemSession session, ResolutionGroup group) {
        List<ArtifactDownload> downloads = gatherDownloads(session, group);
        if (downloads.isEmpty()) {
            return;
        }

        for (ArtifactDownload download : downloads) {
            artifactDownloading(session, download.getTrace(), download.getArtifact(), group.repository);
        }

        try {
            try (RepositoryConnector connector =
                    repositoryConnectorProvider.newRepositoryConnector(session, group.repository)) {
                connector.get(downloads, null);
            }
        } catch (NoRepositoryConnectorException e) {
            for (ArtifactDownload download : downloads) {
                download.setException(new ArtifactTransferException(download.getArtifact(), group.repository, e));
            }
        }

        evaluateDownloads(session, group);
    }

    private List<ArtifactDownload> gatherDownloads(RepositorySystemSession session, ResolutionGroup group) {
        LocalRepositoryManager lrm = session.getLocalRepositoryManager();
        List<ArtifactDownload> downloads = new ArrayList<>();

        for (ResolutionItem item : group.items) {
            Artifact artifact = item.artifact;

            if (item.resolved.get()) {
                // resolved in previous resolution group
                continue;
            }

            ArtifactDownload download = new ArtifactDownload();
            download.setArtifact(artifact);
            download.setRequestContext(item.request.getRequestContext());
            download.setListener(SafeTransferListener.wrap(session));
            download.setTrace(item.trace);
            if (item.local.getPath() != null) {
                download.setPath(item.local.getPath());
                download.setExistenceCheck(true);
            } else {
                download.setPath(lrm.getAbsolutePathForRemoteArtifact(
                        artifact, group.repository, item.request.getRequestContext()));
            }

            boolean snapshot = artifact.isSnapshot();
            RepositoryPolicy policy = remoteRepositoryManager.getPolicy(session, group.repository, !snapshot, snapshot);

            int errorPolicy = Utils.getPolicy(session, artifact, group.repository);
            if ((errorPolicy & ResolutionErrorPolicy.CACHE_ALL) != 0) {
                UpdateCheck<Artifact, ArtifactTransferException> check = new UpdateCheck<>();
                check.setItem(artifact);
                check.setPath(download.getPath());
                check.setFileValid(false);
                check.setRepository(group.repository);
                check.setArtifactPolicy(policy.getArtifactUpdatePolicy());
                check.setMetadataPolicy(policy.getMetadataUpdatePolicy());
                item.updateCheck = check;
                updateCheckManager.checkArtifact(session, check);
                if (!check.isRequired()) {
                    item.result.addException(group.repository, check.getException());
                    continue;
                }
            }

            download.setChecksumPolicy(policy.getChecksumPolicy());
            download.setRepositories(item.repository.getMirroredRepositories());
            downloads.add(download);
            item.download = download;
        }

        return downloads;
    }

    private void evaluateDownloads(RepositorySystemSession session, ResolutionGroup group) {
        LocalRepositoryManager lrm = session.getLocalRepositoryManager();

        for (ResolutionItem item : group.items) {
            ArtifactDownload download = item.download;
            if (download == null) {
                continue;
            }

            Artifact artifact = download.getArtifact();
            if (download.getException() == null) {
                item.resolved.set(true);
                item.result.setRepository(group.repository);
                try {
                    artifact = artifact.setPath(getPath(session, artifact, download.getPath()));
                    item.result.setArtifact(artifact);

                    lrm.add(
                            session,
                            new LocalArtifactRegistration(artifact, group.repository, download.getSupportedContexts()));
                } catch (ArtifactTransferException e) {
                    download.setException(e);
                    item.result.addException(group.repository, e);
                }
            } else {
                item.result.addException(group.repository, download.getException());
            }

            /*
             * NOTE: Touch after registration with local repo to ensure concurrent resolution is not rejected with
             * "already updated" via session data when actual update to local repo is still pending.
             */
            if (item.updateCheck != null) {
                item.updateCheck.setException(download.getException());
                updateCheckManager.touchArtifact(session, item.updateCheck);
            }

            artifactDownloaded(session, download.getTrace(), artifact, group.repository, download.getException());
            if (download.getException() == null) {
                artifactResolved(session, download.getTrace(), artifact, group.repository, null);
            }
        }
    }

    private void artifactResolving(RepositorySystemSession session, RequestTrace trace, Artifact artifact) {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.ARTIFACT_RESOLVING);
        event.setTrace(trace);
        event.setArtifact(artifact);

        repositoryEventDispatcher.dispatch(event.build());
    }

    private void artifactResolved(
            RepositorySystemSession session,
            RequestTrace trace,
            Artifact artifact,
            ArtifactRepository repository,
            Collection<Exception> exceptions) {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.ARTIFACT_RESOLVED);
        event.setTrace(trace);
        event.setArtifact(artifact);
        event.setRepository(repository);
        event.setExceptions(exceptions != null ? new ArrayList<>(exceptions) : null);
        if (artifact != null) {
            event.setPath(artifact.getPath());
        }

        repositoryEventDispatcher.dispatch(event.build());
    }

    private void artifactDownloading(
            RepositorySystemSession session, RequestTrace trace, Artifact artifact, RemoteRepository repository) {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.ARTIFACT_DOWNLOADING);
        event.setTrace(trace);
        event.setArtifact(artifact);
        event.setRepository(repository);

        repositoryEventDispatcher.dispatch(event.build());
    }

    private void artifactDownloaded(
            RepositorySystemSession session,
            RequestTrace trace,
            Artifact artifact,
            RemoteRepository repository,
            Exception exception) {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.ARTIFACT_DOWNLOADED);
        event.setTrace(trace);
        event.setArtifact(artifact);
        event.setRepository(repository);
        event.setException(exception);
        if (artifact != null) {
            event.setPath(artifact.getPath());
        }

        repositoryEventDispatcher.dispatch(event.build());
    }

    static class ResolutionGroup {

        final RemoteRepository repository;

        final List<ResolutionItem> items = new ArrayList<>();

        ResolutionGroup(RemoteRepository repository) {
            this.repository = repository;
        }

        boolean matches(RemoteRepository repo) {
            return repository.getUrl().equals(repo.getUrl())
                    && repository.getContentType().equals(repo.getContentType())
                    && repository.isRepositoryManager() == repo.isRepositoryManager();
        }
    }

    static class ResolutionItem {

        final RequestTrace trace;

        final ArtifactRequest request;

        final ArtifactResult result;

        final LocalArtifactResult local;

        final RemoteRepository repository;

        final Artifact artifact;

        final AtomicBoolean resolved;

        ArtifactDownload download;

        UpdateCheck<Artifact, ArtifactTransferException> updateCheck;

        ResolutionItem(
                RequestTrace trace,
                Artifact artifact,
                AtomicBoolean resolved,
                ArtifactResult result,
                LocalArtifactResult local,
                RemoteRepository repository) {
            this.trace = trace;
            this.artifact = artifact;
            this.resolved = resolved;
            this.result = result;
            this.request = result.getRequest();
            this.local = local;
            this.repository = repository;
        }
    }
}
