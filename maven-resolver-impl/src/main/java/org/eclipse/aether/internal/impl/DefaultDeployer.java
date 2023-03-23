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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.impl.Deployer;
import org.eclipse.aether.impl.MetadataGenerator;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.impl.UpdateCheck;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.metadata.MergeableMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transform.FileTransformer;
import org.eclipse.aether.transform.FileTransformerManager;

import static java.util.Objects.requireNonNull;

/**
 */
@Singleton
@Named
public class DefaultDeployer implements Deployer, Service {
    private FileProcessor fileProcessor;

    private RepositoryEventDispatcher repositoryEventDispatcher;

    private RepositoryConnectorProvider repositoryConnectorProvider;

    private RemoteRepositoryManager remoteRepositoryManager;

    private UpdateCheckManager updateCheckManager;

    private Collection<MetadataGeneratorFactory> metadataFactories = new ArrayList<>();

    private SyncContextFactory syncContextFactory;

    private OfflineController offlineController;

    public DefaultDeployer() {
        // enables default constructor
    }

    @SuppressWarnings("checkstyle:parameternumber")
    @Inject
    DefaultDeployer(
            FileProcessor fileProcessor,
            RepositoryEventDispatcher repositoryEventDispatcher,
            RepositoryConnectorProvider repositoryConnectorProvider,
            RemoteRepositoryManager remoteRepositoryManager,
            UpdateCheckManager updateCheckManager,
            Set<MetadataGeneratorFactory> metadataFactories,
            SyncContextFactory syncContextFactory,
            OfflineController offlineController) {
        setFileProcessor(fileProcessor);
        setRepositoryEventDispatcher(repositoryEventDispatcher);
        setRepositoryConnectorProvider(repositoryConnectorProvider);
        setRemoteRepositoryManager(remoteRepositoryManager);
        setUpdateCheckManager(updateCheckManager);
        setMetadataGeneratorFactories(metadataFactories);
        setSyncContextFactory(syncContextFactory);
        setOfflineController(offlineController);
    }

    public void initService(ServiceLocator locator) {
        setFileProcessor(locator.getService(FileProcessor.class));
        setRepositoryEventDispatcher(locator.getService(RepositoryEventDispatcher.class));
        setRepositoryConnectorProvider(locator.getService(RepositoryConnectorProvider.class));
        setRemoteRepositoryManager(locator.getService(RemoteRepositoryManager.class));
        setUpdateCheckManager(locator.getService(UpdateCheckManager.class));
        setMetadataGeneratorFactories(locator.getServices(MetadataGeneratorFactory.class));
        setSyncContextFactory(locator.getService(SyncContextFactory.class));
        setOfflineController(locator.getService(OfflineController.class));
    }

    public DefaultDeployer setFileProcessor(FileProcessor fileProcessor) {
        this.fileProcessor = requireNonNull(fileProcessor, "file processor cannot be null");
        return this;
    }

    public DefaultDeployer setRepositoryEventDispatcher(RepositoryEventDispatcher repositoryEventDispatcher) {
        this.repositoryEventDispatcher =
                requireNonNull(repositoryEventDispatcher, "repository event dispatcher cannot be null");
        return this;
    }

    public DefaultDeployer setRepositoryConnectorProvider(RepositoryConnectorProvider repositoryConnectorProvider) {
        this.repositoryConnectorProvider =
                requireNonNull(repositoryConnectorProvider, "repository connector provider cannot be null");
        return this;
    }

    public DefaultDeployer setRemoteRepositoryManager(RemoteRepositoryManager remoteRepositoryManager) {
        this.remoteRepositoryManager =
                requireNonNull(remoteRepositoryManager, "remote repository provider cannot be null");
        return this;
    }

    public DefaultDeployer setUpdateCheckManager(UpdateCheckManager updateCheckManager) {
        this.updateCheckManager = requireNonNull(updateCheckManager, "update check manager cannot be null");
        return this;
    }

    public DefaultDeployer addMetadataGeneratorFactory(MetadataGeneratorFactory factory) {
        metadataFactories.add(requireNonNull(factory, "metadata generator factory cannot be null"));
        return this;
    }

    public DefaultDeployer setMetadataGeneratorFactories(Collection<MetadataGeneratorFactory> metadataFactories) {
        if (metadataFactories == null) {
            this.metadataFactories = new ArrayList<>();
        } else {
            this.metadataFactories = metadataFactories;
        }
        return this;
    }

    public DefaultDeployer setSyncContextFactory(SyncContextFactory syncContextFactory) {
        this.syncContextFactory = requireNonNull(syncContextFactory, "sync context factory cannot be null");
        return this;
    }

    public DefaultDeployer setOfflineController(OfflineController offlineController) {
        this.offlineController = requireNonNull(offlineController, "offline controller cannot be null");
        return this;
    }

    public DeployResult deploy(RepositorySystemSession session, DeployRequest request) throws DeploymentException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");
        try {
            Utils.checkOffline(session, offlineController, request.getRepository());
        } catch (RepositoryOfflineException e) {
            throw new DeploymentException(
                    "Cannot deploy while " + request.getRepository().getId() + " ("
                            + request.getRepository().getUrl() + ") is in offline mode",
                    e);
        }

        try (SyncContext syncContext = syncContextFactory.newInstance(session, true)) {
            return deploy(syncContext, session, request);
        }
    }

    private DeployResult deploy(SyncContext syncContext, RepositorySystemSession session, DeployRequest request)
            throws DeploymentException {
        DeployResult result = new DeployResult(request);

        RequestTrace trace = RequestTrace.newChild(request.getTrace(), request);

        RemoteRepository repository = request.getRepository();

        RepositoryConnector connector;
        try {
            connector = repositoryConnectorProvider.newRepositoryConnector(session, repository);
        } catch (NoRepositoryConnectorException e) {
            throw new DeploymentException("Failed to deploy artifacts/metadata: " + e.getMessage(), e);
        }

        try {
            List<? extends MetadataGenerator> generators = getMetadataGenerators(session, request);

            FileTransformerManager fileTransformerManager = session.getFileTransformerManager();

            List<ArtifactUpload> artifactUploads = new ArrayList<>();
            List<MetadataUpload> metadataUploads = new ArrayList<>();
            IdentityHashMap<Metadata, Object> processedMetadata = new IdentityHashMap<>();

            EventCatapult catapult = new EventCatapult(session, trace, repository, repositoryEventDispatcher);

            List<Artifact> artifacts = new ArrayList<>(request.getArtifacts());

            List<Metadata> metadatas = Utils.prepareMetadata(generators, artifacts);

            syncContext.acquire(artifacts, Utils.combine(request.getMetadata(), metadatas));

            for (Metadata metadata : metadatas) {
                upload(metadataUploads, session, metadata, repository, connector, catapult);
                processedMetadata.put(metadata, null);
            }

            for (ListIterator<Artifact> iterator = artifacts.listIterator(); iterator.hasNext(); ) {
                Artifact artifact = iterator.next();

                for (MetadataGenerator generator : generators) {
                    artifact = generator.transformArtifact(artifact);
                }

                iterator.set(artifact);

                Collection<FileTransformer> fileTransformers =
                        fileTransformerManager.getTransformersForArtifact(artifact);
                if (!fileTransformers.isEmpty()) {
                    for (FileTransformer fileTransformer : fileTransformers) {
                        Artifact targetArtifact = fileTransformer.transformArtifact(artifact);

                        ArtifactUpload upload = new ArtifactUpload(targetArtifact, artifact.getFile(), fileTransformer);
                        upload.setTrace(trace);
                        upload.setListener(new ArtifactUploadListener(catapult, upload));
                        artifactUploads.add(upload);
                    }
                } else {
                    ArtifactUpload upload = new ArtifactUpload(artifact, artifact.getFile());
                    upload.setTrace(trace);
                    upload.setListener(new ArtifactUploadListener(catapult, upload));
                    artifactUploads.add(upload);
                }
            }

            connector.put(artifactUploads, null);

            for (ArtifactUpload upload : artifactUploads) {
                if (upload.getException() != null) {
                    throw new DeploymentException(
                            "Failed to deploy artifacts: "
                                    + upload.getException().getMessage(),
                            upload.getException());
                }
                result.addArtifact(upload.getArtifact());
            }

            metadatas = Utils.finishMetadata(generators, artifacts);

            syncContext.acquire(null, metadatas);

            for (Metadata metadata : metadatas) {
                upload(metadataUploads, session, metadata, repository, connector, catapult);
                processedMetadata.put(metadata, null);
            }

            for (Metadata metadata : request.getMetadata()) {
                if (!processedMetadata.containsKey(metadata)) {
                    upload(metadataUploads, session, metadata, repository, connector, catapult);
                    processedMetadata.put(metadata, null);
                }
            }

            connector.put(null, metadataUploads);

            for (MetadataUpload upload : metadataUploads) {
                if (upload.getException() != null) {
                    throw new DeploymentException(
                            "Failed to deploy metadata: "
                                    + upload.getException().getMessage(),
                            upload.getException());
                }
                result.addMetadata(upload.getMetadata());
            }
        } finally {
            connector.close();
        }

        return result;
    }

    private List<? extends MetadataGenerator> getMetadataGenerators(
            RepositorySystemSession session, DeployRequest request) {
        PrioritizedComponents<MetadataGeneratorFactory> factories =
                Utils.sortMetadataGeneratorFactories(session, this.metadataFactories);

        List<MetadataGenerator> generators = new ArrayList<>();

        for (PrioritizedComponent<MetadataGeneratorFactory> factory : factories.getEnabled()) {
            MetadataGenerator generator = factory.getComponent().newInstance(session, request);
            if (generator != null) {
                generators.add(generator);
            }
        }

        return generators;
    }

    private void upload(
            Collection<MetadataUpload> metadataUploads,
            RepositorySystemSession session,
            Metadata metadata,
            RemoteRepository repository,
            RepositoryConnector connector,
            EventCatapult catapult)
            throws DeploymentException {
        LocalRepositoryManager lrm = session.getLocalRepositoryManager();
        File basedir = lrm.getRepository().getBasedir();

        File dstFile = new File(basedir, lrm.getPathForRemoteMetadata(metadata, repository, ""));

        if (metadata instanceof MergeableMetadata) {
            if (!((MergeableMetadata) metadata).isMerged()) {
                RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.METADATA_RESOLVING);
                event.setTrace(catapult.getTrace());
                event.setMetadata(metadata);
                event.setRepository(repository);
                repositoryEventDispatcher.dispatch(event.build());

                event = new RepositoryEvent.Builder(session, EventType.METADATA_DOWNLOADING);
                event.setTrace(catapult.getTrace());
                event.setMetadata(metadata);
                event.setRepository(repository);
                repositoryEventDispatcher.dispatch(event.build());

                RepositoryPolicy policy = getPolicy(session, repository, metadata.getNature());
                MetadataDownload download = new MetadataDownload();
                download.setMetadata(metadata);
                download.setFile(dstFile);
                download.setChecksumPolicy(policy.getChecksumPolicy());
                download.setListener(SafeTransferListener.wrap(session));
                download.setTrace(catapult.getTrace());
                connector.get(null, Collections.singletonList(download));

                Exception error = download.getException();

                if (error instanceof MetadataNotFoundException) {
                    dstFile.delete();
                }

                event = new RepositoryEvent.Builder(session, EventType.METADATA_DOWNLOADED);
                event.setTrace(catapult.getTrace());
                event.setMetadata(metadata);
                event.setRepository(repository);
                event.setException(error);
                event.setFile(dstFile);
                repositoryEventDispatcher.dispatch(event.build());

                event = new RepositoryEvent.Builder(session, EventType.METADATA_RESOLVED);
                event.setTrace(catapult.getTrace());
                event.setMetadata(metadata);
                event.setRepository(repository);
                event.setException(error);
                event.setFile(dstFile);
                repositoryEventDispatcher.dispatch(event.build());

                if (error != null && !(error instanceof MetadataNotFoundException)) {
                    throw new DeploymentException(
                            "Failed to retrieve remote metadata " + metadata + ": " + error.getMessage(), error);
                }
            }

            try {
                ((MergeableMetadata) metadata).merge(dstFile, dstFile);
            } catch (RepositoryException e) {
                throw new DeploymentException("Failed to update metadata " + metadata + ": " + e.getMessage(), e);
            }
        } else {
            if (metadata.getFile() == null) {
                throw new DeploymentException("Failed to update metadata " + metadata + ": No file attached.");
            }
            try {
                fileProcessor.copy(metadata.getFile(), dstFile);
            } catch (IOException e) {
                throw new DeploymentException("Failed to update metadata " + metadata + ": " + e.getMessage(), e);
            }
        }

        UpdateCheck<Metadata, MetadataTransferException> check = new UpdateCheck<>();
        check.setItem(metadata);
        check.setFile(dstFile);
        check.setRepository(repository);
        check.setAuthoritativeRepository(repository);
        updateCheckManager.touchMetadata(session, check);

        MetadataUpload upload = new MetadataUpload(metadata, dstFile);
        upload.setTrace(catapult.getTrace());
        upload.setListener(new MetadataUploadListener(catapult, upload));
        metadataUploads.add(upload);
    }

    private RepositoryPolicy getPolicy(
            RepositorySystemSession session, RemoteRepository repository, Metadata.Nature nature) {
        boolean releases = !Metadata.Nature.SNAPSHOT.equals(nature);
        boolean snapshots = !Metadata.Nature.RELEASE.equals(nature);
        return remoteRepositoryManager.getPolicy(session, repository, releases, snapshots);
    }

    static final class EventCatapult {

        private final RepositorySystemSession session;

        private final RequestTrace trace;

        private final RemoteRepository repository;

        private final RepositoryEventDispatcher dispatcher;

        EventCatapult(
                RepositorySystemSession session,
                RequestTrace trace,
                RemoteRepository repository,
                RepositoryEventDispatcher dispatcher) {
            this.session = session;
            this.trace = trace;
            this.repository = repository;
            this.dispatcher = dispatcher;
        }

        public RepositorySystemSession getSession() {
            return session;
        }

        public RequestTrace getTrace() {
            return trace;
        }

        public void artifactDeploying(Artifact artifact, File file) {
            RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.ARTIFACT_DEPLOYING);
            event.setTrace(trace);
            event.setArtifact(artifact);
            event.setRepository(repository);
            event.setFile(file);

            dispatcher.dispatch(event.build());
        }

        public void artifactDeployed(Artifact artifact, File file, ArtifactTransferException exception) {
            RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.ARTIFACT_DEPLOYED);
            event.setTrace(trace);
            event.setArtifact(artifact);
            event.setRepository(repository);
            event.setFile(file);
            event.setException(exception);

            dispatcher.dispatch(event.build());
        }

        public void metadataDeploying(Metadata metadata, File file) {
            RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.METADATA_DEPLOYING);
            event.setTrace(trace);
            event.setMetadata(metadata);
            event.setRepository(repository);
            event.setFile(file);

            dispatcher.dispatch(event.build());
        }

        public void metadataDeployed(Metadata metadata, File file, Exception exception) {
            RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.METADATA_DEPLOYED);
            event.setTrace(trace);
            event.setMetadata(metadata);
            event.setRepository(repository);
            event.setFile(file);
            event.setException(exception);

            dispatcher.dispatch(event.build());
        }
    }

    static final class ArtifactUploadListener extends SafeTransferListener {

        private final EventCatapult catapult;

        private final ArtifactUpload transfer;

        ArtifactUploadListener(EventCatapult catapult, ArtifactUpload transfer) {
            super(catapult.getSession());
            this.catapult = catapult;
            this.transfer = transfer;
        }

        @Override
        public void transferInitiated(TransferEvent event) throws TransferCancelledException {
            super.transferInitiated(event);
            requireNonNull(event, "event cannot be null");
            catapult.artifactDeploying(transfer.getArtifact(), transfer.getFile());
        }

        @Override
        public void transferFailed(TransferEvent event) {
            super.transferFailed(event);
            requireNonNull(event, "event cannot be null");
            catapult.artifactDeployed(transfer.getArtifact(), transfer.getFile(), transfer.getException());
        }

        @Override
        public void transferSucceeded(TransferEvent event) {
            super.transferSucceeded(event);
            requireNonNull(event, "event cannot be null");
            catapult.artifactDeployed(transfer.getArtifact(), transfer.getFile(), null);
        }
    }

    static final class MetadataUploadListener extends SafeTransferListener {

        private final EventCatapult catapult;

        private final MetadataUpload transfer;

        MetadataUploadListener(EventCatapult catapult, MetadataUpload transfer) {
            super(catapult.getSession());
            this.catapult = catapult;
            this.transfer = transfer;
        }

        @Override
        public void transferInitiated(TransferEvent event) throws TransferCancelledException {
            super.transferInitiated(event);
            requireNonNull(event, "event cannot be null");
            catapult.metadataDeploying(transfer.getMetadata(), transfer.getFile());
        }

        @Override
        public void transferFailed(TransferEvent event) {
            super.transferFailed(event);
            requireNonNull(event, "event cannot be null");
            catapult.metadataDeployed(transfer.getMetadata(), transfer.getFile(), transfer.getException());
        }

        @Override
        public void transferSucceeded(TransferEvent event) {
            super.transferSucceeded(event);
            requireNonNull(event, "event cannot be null");
            catapult.metadataDeployed(transfer.getMetadata(), transfer.getFile(), null);
        }
    }
}
