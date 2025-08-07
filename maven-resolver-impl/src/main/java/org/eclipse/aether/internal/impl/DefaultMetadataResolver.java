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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.impl.RemoteRepositoryFilterManager;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.impl.UpdateCheck;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.concurrency.RunnableErrorForwarder;
import org.eclipse.aether.util.concurrency.SmartExecutor;
import org.eclipse.aether.util.concurrency.SmartExecutorUtils;

import static java.util.Objects.requireNonNull;

/**
 */
@Singleton
@Named
public class DefaultMetadataResolver implements MetadataResolver {
    private static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_AETHER + "metadataResolver.";

    /**
     * Number of threads to use in parallel for resolving metadata.
     *
     * @since 0.9.0.M4
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_THREADS}
     */
    public static final String CONFIG_PROP_THREADS = CONFIG_PROPS_PREFIX + "threads";

    public static final int DEFAULT_THREADS = 4;

    private final RepositoryEventDispatcher repositoryEventDispatcher;

    private final UpdateCheckManager updateCheckManager;

    private final RepositoryConnectorProvider repositoryConnectorProvider;

    private final RemoteRepositoryManager remoteRepositoryManager;

    private final SyncContextFactory syncContextFactory;

    private final OfflineController offlineController;

    private final RemoteRepositoryFilterManager remoteRepositoryFilterManager;

    private final PathProcessor pathProcessor;

    @SuppressWarnings("checkstyle:parameternumber")
    @Inject
    public DefaultMetadataResolver(
            RepositoryEventDispatcher repositoryEventDispatcher,
            UpdateCheckManager updateCheckManager,
            RepositoryConnectorProvider repositoryConnectorProvider,
            RemoteRepositoryManager remoteRepositoryManager,
            SyncContextFactory syncContextFactory,
            OfflineController offlineController,
            RemoteRepositoryFilterManager remoteRepositoryFilterManager,
            PathProcessor pathProcessor) {
        this.repositoryEventDispatcher =
                requireNonNull(repositoryEventDispatcher, "repository event dispatcher cannot be null");
        this.updateCheckManager = requireNonNull(updateCheckManager, "update check manager cannot be null");
        this.repositoryConnectorProvider =
                requireNonNull(repositoryConnectorProvider, "repository connector provider cannot be null");
        this.remoteRepositoryManager =
                requireNonNull(remoteRepositoryManager, "remote repository provider cannot be null");
        this.syncContextFactory = requireNonNull(syncContextFactory, "sync context factory cannot be null");
        this.offlineController = requireNonNull(offlineController, "offline controller cannot be null");
        this.remoteRepositoryFilterManager =
                requireNonNull(remoteRepositoryFilterManager, "remote repository filter manager cannot be null");
        this.pathProcessor = requireNonNull(pathProcessor, "path processor cannot be null");
    }

    @Override
    public List<MetadataResult> resolveMetadata(
            RepositorySystemSession session, Collection<? extends MetadataRequest> requests) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(requests, "requests cannot be null");
        try (SyncContext shared = syncContextFactory.newInstance(session, true);
                SyncContext exclusive = syncContextFactory.newInstance(session, false)) {
            Collection<Metadata> metadata = new ArrayList<>(requests.size());
            for (MetadataRequest request : requests) {
                metadata.add(request.getMetadata());
            }

            return resolve(shared, exclusive, metadata, session, requests);
        }
    }

    @SuppressWarnings("checkstyle:methodlength")
    private List<MetadataResult> resolve(
            SyncContext shared,
            SyncContext exclusive,
            Collection<Metadata> subjects,
            RepositorySystemSession session,
            Collection<? extends MetadataRequest> requests) {
        SyncContext current = shared;
        try {
            while (true) {
                current.acquire(null, subjects);

                final List<MetadataResult> results = new ArrayList<>(requests.size());
                final List<ResolveTask> tasks = new ArrayList<>(requests.size());
                final Map<Path, Long> localLastUpdates = new HashMap<>();
                final RemoteRepositoryFilter remoteRepositoryFilter =
                        remoteRepositoryFilterManager.getRemoteRepositoryFilter(session);

                for (MetadataRequest request : requests) {
                    RequestTrace trace = RequestTrace.newChild(request.getTrace(), request);

                    MetadataResult result = new MetadataResult(request);
                    results.add(result);

                    Metadata metadata = request.getMetadata();
                    RemoteRepository repository = request.getRepository();

                    if (repository == null) {
                        LocalRepository localRepo =
                                session.getLocalRepositoryManager().getRepository();

                        metadataResolving(session, trace, metadata, localRepo);

                        Path localFile = getLocalFile(session, metadata);

                        if (localFile != null) {
                            metadata = metadata.setPath(localFile);
                            result.setMetadata(metadata);
                        } else {
                            result.setException(new MetadataNotFoundException(metadata, localRepo));
                        }

                        metadataResolved(session, trace, metadata, localRepo, result.getException());
                        continue;
                    }

                    if (remoteRepositoryFilter != null) {
                        RemoteRepositoryFilter.Result filterResult =
                                remoteRepositoryFilter.acceptMetadata(repository, metadata);
                        if (!filterResult.isAccepted()) {
                            result.setException(
                                    new MetadataNotFoundException(metadata, repository, filterResult.reasoning()));
                            continue;
                        }
                    }

                    List<RemoteRepository> repositories =
                            getEnabledSourceRepositories(repository, metadata.getNature());

                    if (repositories.isEmpty()) {
                        continue;
                    }

                    metadataResolving(session, trace, metadata, repository);
                    LocalRepositoryManager lrm = session.getLocalRepositoryManager();
                    LocalMetadataRequest localRequest =
                            new LocalMetadataRequest(metadata, repository, request.getRequestContext());
                    LocalMetadataResult lrmResult = lrm.find(session, localRequest);

                    Path metadataPath = lrmResult.getPath();

                    try {
                        Utils.checkOffline(session, offlineController, repository);
                    } catch (RepositoryOfflineException e) {
                        if (metadataPath != null) {
                            metadata = metadata.setPath(metadataPath);
                            result.setMetadata(metadata);
                        } else {
                            String msg = "Cannot access " + repository.getId() + " (" + repository.getUrl()
                                    + ") in offline mode and the metadata " + metadata
                                    + " has not been downloaded from it before";
                            result.setException(new MetadataNotFoundException(metadata, repository, msg, e));
                        }

                        metadataResolved(session, trace, metadata, repository, result.getException());
                        continue;
                    }

                    Long localLastUpdate = null;
                    if (request.isFavorLocalRepository()) {
                        Path localPath = getLocalFile(session, metadata);
                        localLastUpdate = localLastUpdates.get(localPath);
                        if (localLastUpdate == null) {
                            localLastUpdate = localPath != null ? pathProcessor.lastModified(localPath, 0L) : 0L;
                            localLastUpdates.put(localPath, localLastUpdate);
                        }
                    }

                    List<UpdateCheck<Metadata, MetadataTransferException>> checks = new ArrayList<>();
                    Exception exception = null;
                    for (RemoteRepository repo : repositories) {
                        RepositoryPolicy policy = getPolicy(session, repo, metadata.getNature());

                        UpdateCheck<Metadata, MetadataTransferException> check = new UpdateCheck<>();
                        check.setLocalLastUpdated((localLastUpdate != null) ? localLastUpdate : 0);
                        check.setItem(metadata);

                        // use 'main' installation file for the check (-> use requested repository)
                        Path checkPath = session.getLocalRepositoryManager()
                                .getAbsolutePathForRemoteMetadata(metadata, repository, request.getRequestContext());
                        check.setPath(checkPath);
                        check.setRepository(repository);
                        check.setAuthoritativeRepository(repo);
                        check.setArtifactPolicy(policy.getArtifactUpdatePolicy());
                        check.setMetadataPolicy(policy.getMetadataUpdatePolicy());

                        if (lrmResult.isStale()) {
                            checks.add(check);
                        } else {
                            updateCheckManager.checkMetadata(session, check);
                            if (check.isRequired()) {
                                checks.add(check);
                            } else if (exception == null) {
                                exception = check.getException();
                            }
                        }
                    }

                    if (!checks.isEmpty()) {
                        RepositoryPolicy policy = getPolicy(session, repository, metadata.getNature());

                        // install path may be different from lookup path
                        Path installPath = session.getLocalRepositoryManager()
                                .getAbsolutePathForRemoteMetadata(
                                        metadata, request.getRepository(), request.getRequestContext());

                        ResolveTask task = new ResolveTask(
                                session, trace, result, installPath, checks, policy.getChecksumPolicy());
                        tasks.add(task);
                    } else {
                        result.setException(exception);
                        if (metadataPath != null) {
                            metadata = metadata.setPath(metadataPath);
                            result.setMetadata(metadata);
                        }
                        metadataResolved(session, trace, metadata, repository, result.getException());
                    }
                }

                if (!tasks.isEmpty() && current == shared) {
                    current.close();
                    current = exclusive;
                    continue;
                }

                if (!tasks.isEmpty()) {
                    try (SmartExecutor executor = SmartExecutorUtils.newSmartExecutor(
                            tasks.size(),
                            ConfigUtils.getInteger(session, DEFAULT_THREADS, CONFIG_PROP_THREADS),
                            getClass().getSimpleName() + "-")) {
                        RunnableErrorForwarder errorForwarder = new RunnableErrorForwarder();

                        for (ResolveTask task : tasks) {
                            metadataDownloading(
                                    task.session, task.trace, task.request.getMetadata(), task.request.getRepository());

                            executor.submit(errorForwarder.wrap(task));
                        }

                        errorForwarder.await();

                        for (ResolveTask task : tasks) {
                            /*
                             * NOTE: Touch after registration with local repo to ensure concurrent resolution is not
                             * rejected with "already updated" via session data when actual update to local repo is
                             * still pending.
                             */
                            for (UpdateCheck<Metadata, MetadataTransferException> check : task.checks) {
                                updateCheckManager.touchMetadata(task.session, check.setException(task.exception));
                            }

                            metadataDownloaded(
                                    session,
                                    task.trace,
                                    task.request.getMetadata(),
                                    task.request.getRepository(),
                                    task.metadataPath,
                                    task.exception);

                            task.result.setException(task.exception);
                        }
                    }
                    for (ResolveTask task : tasks) {
                        Metadata metadata = task.request.getMetadata();
                        // re-lookup metadata for resolve
                        LocalMetadataRequest localRequest = new LocalMetadataRequest(
                                metadata, task.request.getRepository(), task.request.getRequestContext());
                        Path metadataPath = session.getLocalRepositoryManager()
                                .find(session, localRequest)
                                .getPath();
                        if (metadataPath != null) {
                            metadata = metadata.setPath(metadataPath);
                            task.result.setMetadata(metadata);
                        }
                        if (task.result.getException() == null) {
                            task.result.setUpdated(true);
                        }
                        metadataResolved(
                                session,
                                task.trace,
                                metadata,
                                task.request.getRepository(),
                                task.result.getException());
                    }
                }

                return results;
            }
        } finally {
            current.close();
        }
    }

    private Path getLocalFile(RepositorySystemSession session, Metadata metadata) {
        LocalRepositoryManager lrm = session.getLocalRepositoryManager();
        LocalMetadataResult localResult = lrm.find(session, new LocalMetadataRequest(metadata, null, null));
        return localResult.getPath();
    }

    private List<RemoteRepository> getEnabledSourceRepositories(RemoteRepository repository, Metadata.Nature nature) {
        List<RemoteRepository> repositories = new ArrayList<>();

        if (repository.isRepositoryManager()) {
            for (RemoteRepository repo : repository.getMirroredRepositories()) {
                if (isEnabled(repo, nature)) {
                    repositories.add(repo);
                }
            }
        } else if (isEnabled(repository, nature)) {
            repositories.add(repository);
        }

        return repositories;
    }

    private boolean isEnabled(RemoteRepository repository, Metadata.Nature nature) {
        if (!Metadata.Nature.SNAPSHOT.equals(nature)
                && repository.getPolicy(false).isEnabled()) {
            return true;
        }
        return !Metadata.Nature.RELEASE.equals(nature)
                && repository.getPolicy(true).isEnabled();
    }

    private RepositoryPolicy getPolicy(
            RepositorySystemSession session, RemoteRepository repository, Metadata.Nature nature) {
        boolean releases = !Metadata.Nature.SNAPSHOT.equals(nature);
        boolean snapshots = !Metadata.Nature.RELEASE.equals(nature);
        return remoteRepositoryManager.getPolicy(session, repository, releases, snapshots);
    }

    private void metadataResolving(
            RepositorySystemSession session, RequestTrace trace, Metadata metadata, ArtifactRepository repository) {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.METADATA_RESOLVING);
        event.setTrace(trace);
        event.setMetadata(metadata);
        event.setRepository(repository);

        repositoryEventDispatcher.dispatch(event.build());
    }

    private void metadataResolved(
            RepositorySystemSession session,
            RequestTrace trace,
            Metadata metadata,
            ArtifactRepository repository,
            Exception exception) {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.METADATA_RESOLVED);
        event.setTrace(trace);
        event.setMetadata(metadata);
        event.setRepository(repository);
        event.setException(exception);
        event.setPath(metadata.getPath());

        repositoryEventDispatcher.dispatch(event.build());
    }

    private void metadataDownloading(
            RepositorySystemSession session, RequestTrace trace, Metadata metadata, ArtifactRepository repository) {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.METADATA_DOWNLOADING);
        event.setTrace(trace);
        event.setMetadata(metadata);
        event.setRepository(repository);

        repositoryEventDispatcher.dispatch(event.build());
    }

    private void metadataDownloaded(
            RepositorySystemSession session,
            RequestTrace trace,
            Metadata metadata,
            ArtifactRepository repository,
            Path path,
            Exception exception) {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.METADATA_DOWNLOADED);
        event.setTrace(trace);
        event.setMetadata(metadata);
        event.setRepository(repository);
        event.setException(exception);
        event.setPath(path);

        repositoryEventDispatcher.dispatch(event.build());
    }

    class ResolveTask implements Runnable {
        final RepositorySystemSession session;

        final RequestTrace trace;

        final MetadataResult result;

        final MetadataRequest request;

        final Path metadataPath;

        final String policy;

        final List<UpdateCheck<Metadata, MetadataTransferException>> checks;

        volatile MetadataTransferException exception;

        ResolveTask(
                RepositorySystemSession session,
                RequestTrace trace,
                MetadataResult result,
                Path metadataPath,
                List<UpdateCheck<Metadata, MetadataTransferException>> checks,
                String policy) {
            this.session = session;
            this.trace = trace;
            this.result = result;
            this.request = result.getRequest();
            this.metadataPath = metadataPath;
            this.policy = policy;
            this.checks = checks;
        }

        public void run() {
            Metadata metadata = request.getMetadata();
            RemoteRepository requestRepository = request.getRepository();

            try {
                List<RemoteRepository> repositories = new ArrayList<>();
                for (UpdateCheck<Metadata, MetadataTransferException> check : checks) {
                    repositories.add(check.getAuthoritativeRepository());
                }

                MetadataDownload download = new MetadataDownload();
                download.setMetadata(metadata);
                download.setRequestContext(request.getRequestContext());
                download.setPath(metadataPath);
                download.setChecksumPolicy(policy);
                download.setRepositories(repositories);
                download.setListener(SafeTransferListener.wrap(session));
                download.setTrace(trace);

                try (RepositoryConnector connector =
                        repositoryConnectorProvider.newRepositoryConnector(session, requestRepository)) {
                    connector.get(null, Collections.singletonList(download));
                }

                exception = download.getException();

                if (exception == null) {

                    List<String> contexts = Collections.singletonList(request.getRequestContext());
                    LocalMetadataRegistration registration =
                            new LocalMetadataRegistration(metadata, requestRepository, contexts);

                    session.getLocalRepositoryManager().add(session, registration);
                } else if (request.isDeleteLocalCopyIfMissing() && exception instanceof MetadataNotFoundException) {
                    try {
                        Files.deleteIfExists(download.getPath());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            } catch (NoRepositoryConnectorException e) {
                exception = new MetadataTransferException(metadata, requestRepository, e);
            }
        }
    }
}
