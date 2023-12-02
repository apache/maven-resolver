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
package org.eclipse.aether.connector.basic;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.checksums.ProvidedChecksumsSource;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmHelper;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.FileUtils;
import org.eclipse.aether.util.concurrency.ExecutorUtils;
import org.eclipse.aether.util.concurrency.RunnableErrorForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.eclipse.aether.connector.basic.BasicRepositoryConnectorConfigurationKeys.CONFIG_PROP_PARALLEL_PUT;
import static org.eclipse.aether.connector.basic.BasicRepositoryConnectorConfigurationKeys.CONFIG_PROP_PERSISTED_CHECKSUMS;
import static org.eclipse.aether.connector.basic.BasicRepositoryConnectorConfigurationKeys.CONFIG_PROP_SMART_CHECKSUMS;
import static org.eclipse.aether.connector.basic.BasicRepositoryConnectorConfigurationKeys.CONFIG_PROP_THREADS;
import static org.eclipse.aether.connector.basic.BasicRepositoryConnectorConfigurationKeys.DEFAULT_PARALLEL_PUT;
import static org.eclipse.aether.connector.basic.BasicRepositoryConnectorConfigurationKeys.DEFAULT_PERSISTED_CHECKSUMS;
import static org.eclipse.aether.connector.basic.BasicRepositoryConnectorConfigurationKeys.DEFAULT_SMART_CHECKSUMS;
import static org.eclipse.aether.connector.basic.BasicRepositoryConnectorConfigurationKeys.DEFAULT_THREADS;

/**
 *
 */
final class BasicRepositoryConnector implements RepositoryConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicRepositoryConnector.class);

    private final Map<String, ProvidedChecksumsSource> providedChecksumsSources;

    private final FileProcessor fileProcessor;

    private final RemoteRepository repository;

    private final RepositorySystemSession session;

    private final Transporter transporter;

    private final RepositoryLayout layout;

    private final ChecksumPolicyProvider checksumPolicyProvider;

    private final int maxThreads;

    private final boolean smartChecksums;

    private final boolean parallelPut;

    private final boolean persistedChecksums;

    private Executor executor;

    private final AtomicBoolean closed;

    BasicRepositoryConnector(
            RepositorySystemSession session,
            RemoteRepository repository,
            TransporterProvider transporterProvider,
            RepositoryLayoutProvider layoutProvider,
            ChecksumPolicyProvider checksumPolicyProvider,
            FileProcessor fileProcessor,
            Map<String, ProvidedChecksumsSource> providedChecksumsSources)
            throws NoRepositoryConnectorException {
        try {
            layout = layoutProvider.newRepositoryLayout(session, repository);
        } catch (NoRepositoryLayoutException e) {
            throw new NoRepositoryConnectorException(repository, e.getMessage(), e);
        }
        try {
            transporter = transporterProvider.newTransporter(session, repository);
        } catch (NoTransporterException e) {
            throw new NoRepositoryConnectorException(repository, e.getMessage(), e);
        }
        this.checksumPolicyProvider = checksumPolicyProvider;

        this.session = session;
        this.repository = repository;
        this.fileProcessor = fileProcessor;
        this.providedChecksumsSources = providedChecksumsSources;
        this.closed = new AtomicBoolean(false);

        maxThreads = ExecutorUtils.threadCount(session, DEFAULT_THREADS, CONFIG_PROP_THREADS);
        smartChecksums = ConfigUtils.getBoolean(session, DEFAULT_SMART_CHECKSUMS, CONFIG_PROP_SMART_CHECKSUMS);
        parallelPut = ConfigUtils.getBoolean(
                session,
                DEFAULT_PARALLEL_PUT,
                CONFIG_PROP_PARALLEL_PUT + "." + repository.getId(),
                CONFIG_PROP_PARALLEL_PUT);
        persistedChecksums =
                ConfigUtils.getBoolean(session, DEFAULT_PERSISTED_CHECKSUMS, CONFIG_PROP_PERSISTED_CHECKSUMS);
    }

    private Executor getExecutor(int tasks) {
        if (maxThreads <= 1) {
            return ExecutorUtils.DIRECT_EXECUTOR;
        }
        if (tasks <= 1) {
            return ExecutorUtils.DIRECT_EXECUTOR;
        }
        if (executor == null) {
            executor =
                    ExecutorUtils.threadPool(maxThreads, getClass().getSimpleName() + '-' + repository.getHost() + '-');
        }
        return executor;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            ExecutorUtils.shutdown(executor);
            transporter.close();
        }
    }

    private void failIfClosed() {
        if (closed.get()) {
            throw new IllegalStateException("connector already closed");
        }
    }

    @Override
    public void get(
            Collection<? extends ArtifactDownload> artifactDownloads,
            Collection<? extends MetadataDownload> metadataDownloads) {
        failIfClosed();

        Collection<? extends ArtifactDownload> safeArtifactDownloads = safe(artifactDownloads);
        Collection<? extends MetadataDownload> safeMetadataDownloads = safe(metadataDownloads);

        Executor executor = getExecutor(safeArtifactDownloads.size() + safeMetadataDownloads.size());
        RunnableErrorForwarder errorForwarder = new RunnableErrorForwarder();
        List<ChecksumAlgorithmFactory> checksumAlgorithmFactories = layout.getChecksumAlgorithmFactories();

        boolean first = true;

        for (MetadataDownload transfer : safeMetadataDownloads) {
            URI location = layout.getLocation(transfer.getMetadata(), false);

            TransferResource resource = newTransferResource(location, transfer.getFile(), transfer.getTrace());
            TransferEvent.Builder builder = newEventBuilder(resource, false, false);
            MetadataTransportListener listener = new MetadataTransportListener(transfer, repository, builder);

            ChecksumPolicy checksumPolicy = newChecksumPolicy(transfer.getChecksumPolicy(), resource);
            List<RepositoryLayout.ChecksumLocation> checksumLocations = null;
            if (checksumPolicy != null) {
                checksumLocations = layout.getChecksumLocations(transfer.getMetadata(), false, location);
            }

            Runnable task = new GetTaskRunner(
                    location,
                    transfer.getFile(),
                    checksumPolicy,
                    checksumAlgorithmFactories,
                    checksumLocations,
                    null,
                    listener);
            if (first) {
                task.run();
                first = false;
            } else {
                executor.execute(errorForwarder.wrap(task));
            }
        }

        for (ArtifactDownload transfer : safeArtifactDownloads) {
            Map<String, String> providedChecksums = Collections.emptyMap();
            for (ProvidedChecksumsSource providedChecksumsSource : providedChecksumsSources.values()) {
                Map<String, String> provided = providedChecksumsSource.getProvidedArtifactChecksums(
                        session, transfer, repository, checksumAlgorithmFactories);

                if (provided != null) {
                    providedChecksums = provided;
                    break;
                }
            }

            URI location = layout.getLocation(transfer.getArtifact(), false);

            TransferResource resource = newTransferResource(location, transfer.getFile(), transfer.getTrace());
            TransferEvent.Builder builder = newEventBuilder(resource, false, transfer.isExistenceCheck());
            ArtifactTransportListener listener = new ArtifactTransportListener(transfer, repository, builder);

            Runnable task;
            if (transfer.isExistenceCheck()) {
                task = new PeekTaskRunner(location, listener);
            } else {
                ChecksumPolicy checksumPolicy = newChecksumPolicy(transfer.getChecksumPolicy(), resource);
                List<RepositoryLayout.ChecksumLocation> checksumLocations = null;
                if (checksumPolicy != null) {
                    checksumLocations = layout.getChecksumLocations(transfer.getArtifact(), false, location);
                }

                task = new GetTaskRunner(
                        location,
                        transfer.getFile(),
                        checksumPolicy,
                        checksumAlgorithmFactories,
                        checksumLocations,
                        providedChecksums,
                        listener);
            }
            if (first) {
                task.run();
                first = false;
            } else {
                executor.execute(errorForwarder.wrap(task));
            }
        }

        errorForwarder.await();
    }

    @Override
    public void put(
            Collection<? extends ArtifactUpload> artifactUploads,
            Collection<? extends MetadataUpload> metadataUploads) {
        failIfClosed();

        Collection<? extends ArtifactUpload> safeArtifactUploads = safe(artifactUploads);
        Collection<? extends MetadataUpload> safeMetadataUploads = safe(metadataUploads);

        Executor executor = getExecutor(parallelPut ? safeArtifactUploads.size() + safeMetadataUploads.size() : 1);
        RunnableErrorForwarder errorForwarder = new RunnableErrorForwarder();

        boolean first = true;

        for (ArtifactUpload transfer : safeArtifactUploads) {
            URI location = layout.getLocation(transfer.getArtifact(), true);

            TransferResource resource = newTransferResource(location, transfer.getFile(), transfer.getTrace());
            TransferEvent.Builder builder = newEventBuilder(resource, true, false);
            ArtifactTransportListener listener = new ArtifactTransportListener(transfer, repository, builder);

            List<RepositoryLayout.ChecksumLocation> checksumLocations =
                    layout.getChecksumLocations(transfer.getArtifact(), true, location);

            Runnable task = new PutTaskRunner(location, transfer.getFile(), checksumLocations, listener);
            if (first) {
                task.run();
                first = false;
            } else {
                executor.execute(errorForwarder.wrap(task));
            }
        }

        errorForwarder.await(); // make sure all artifacts are PUT before we go with Metadata

        for (List<? extends MetadataUpload> transferGroup : groupUploads(safeMetadataUploads)) {
            for (MetadataUpload transfer : transferGroup) {
                URI location = layout.getLocation(transfer.getMetadata(), true);

                TransferResource resource = newTransferResource(location, transfer.getFile(), transfer.getTrace());
                TransferEvent.Builder builder = newEventBuilder(resource, true, false);
                MetadataTransportListener listener = new MetadataTransportListener(transfer, repository, builder);

                List<RepositoryLayout.ChecksumLocation> checksumLocations =
                        layout.getChecksumLocations(transfer.getMetadata(), true, location);

                Runnable task = new PutTaskRunner(location, transfer.getFile(), checksumLocations, listener);
                if (first) {
                    task.run();
                    first = false;
                } else {
                    executor.execute(errorForwarder.wrap(task));
                }
            }

            errorForwarder.await(); // make sure each group is done before starting next group
        }
    }

    /**
     * This method "groups" the Metadata to be uploaded by their level (version, artifact, group and root). This is MUST
     * as clients consume metadata in opposite order (root, group, artifact, version), and hence, we must deploy and
     * ensure (in case of parallel deploy) that all V level metadata is deployed before we start deploying A level, etc.
     */
    private static List<List<MetadataUpload>> groupUploads(Collection<? extends MetadataUpload> metadataUploads) {
        ArrayList<MetadataUpload> v = new ArrayList<>();
        ArrayList<MetadataUpload> a = new ArrayList<>();
        ArrayList<MetadataUpload> g = new ArrayList<>();
        ArrayList<MetadataUpload> r = new ArrayList<>();

        for (MetadataUpload transfer : metadataUploads) {
            Metadata metadata = transfer.getMetadata();
            if (!"".equals(metadata.getVersion())) {
                v.add(transfer);
            } else if (!"".equals(metadata.getArtifactId())) {
                a.add(transfer);
            } else if (!"".equals(metadata.getGroupId())) {
                g.add(transfer);
            } else {
                r.add(transfer);
            }
        }

        List<List<MetadataUpload>> result = new ArrayList<>(4);
        if (!v.isEmpty()) {
            result.add(v);
        }
        if (!a.isEmpty()) {
            result.add(a);
        }
        if (!g.isEmpty()) {
            result.add(g);
        }
        if (!r.isEmpty()) {
            result.add(r);
        }
        return result;
    }

    private static <T> Collection<T> safe(Collection<T> items) {
        return (items != null) ? items : Collections.emptyList();
    }

    private TransferResource newTransferResource(URI path, File file, RequestTrace trace) {
        return new TransferResource(repository.getId(), repository.getUrl(), path.toString(), file, trace);
    }

    private TransferEvent.Builder newEventBuilder(TransferResource resource, boolean upload, boolean peek) {
        TransferEvent.Builder builder = new TransferEvent.Builder(session, resource);
        if (upload) {
            builder.setRequestType(TransferEvent.RequestType.PUT);
        } else if (!peek) {
            builder.setRequestType(TransferEvent.RequestType.GET);
        } else {
            builder.setRequestType(TransferEvent.RequestType.GET_EXISTENCE);
        }
        return builder;
    }

    private ChecksumPolicy newChecksumPolicy(String policy, TransferResource resource) {
        return checksumPolicyProvider.newChecksumPolicy(session, repository, resource, policy);
    }

    @Override
    public String toString() {
        return String.valueOf(repository);
    }

    abstract class TaskRunner implements Runnable {

        protected final URI path;

        protected final TransferTransportListener<?> listener;

        TaskRunner(URI path, TransferTransportListener<?> listener) {
            this.path = path;
            this.listener = listener;
        }

        @Override
        public void run() {
            try {
                listener.transferInitiated();
                runTask();
                listener.transferSucceeded();
            } catch (Exception e) {
                listener.transferFailed(e, transporter.classify(e));
            }
        }

        protected abstract void runTask() throws Exception;
    }

    class PeekTaskRunner extends TaskRunner {

        PeekTaskRunner(URI path, TransferTransportListener<?> listener) {
            super(path, listener);
        }

        @Override
        protected void runTask() throws Exception {
            transporter.peek(new PeekTask(path));
        }
    }

    class GetTaskRunner extends TaskRunner implements ChecksumValidator.ChecksumFetcher {

        private final File file;

        private final ChecksumValidator checksumValidator;

        GetTaskRunner(
                URI path,
                File file,
                ChecksumPolicy checksumPolicy,
                List<ChecksumAlgorithmFactory> checksumAlgorithmFactories,
                List<RepositoryLayout.ChecksumLocation> checksumLocations,
                Map<String, String> providedChecksums,
                TransferTransportListener<?> listener) {
            super(path, listener);
            this.file = requireNonNull(file, "destination file cannot be null");
            checksumValidator = new ChecksumValidator(
                    file,
                    checksumAlgorithmFactories,
                    fileProcessor,
                    this,
                    checksumPolicy,
                    providedChecksums,
                    safe(checksumLocations));
        }

        @Override
        public boolean fetchChecksum(URI remote, File local) throws Exception {
            try {
                transporter.get(new GetTask(remote).setDataFile(local));
            } catch (Exception e) {
                if (transporter.classify(e) == Transporter.ERROR_NOT_FOUND) {
                    return false;
                }
                throw e;
            }
            return true;
        }

        @Override
        protected void runTask() throws Exception {
            try (FileUtils.CollocatedTempFile tempFile = FileUtils.newTempFile(file.toPath())) {
                final File tmp = tempFile.getPath().toFile();
                listener.setChecksumCalculator(checksumValidator.newChecksumCalculator(tmp));
                for (int firstTrial = 0, lastTrial = 1, trial = firstTrial; ; trial++) {
                    GetTask task = new GetTask(path).setDataFile(tmp, false).setListener(listener);
                    transporter.get(task);
                    try {
                        checksumValidator.validate(
                                listener.getChecksums(), smartChecksums ? task.getChecksums() : null);
                        break;
                    } catch (ChecksumFailureException e) {
                        boolean retry = trial < lastTrial && e.isRetryWorthy();
                        if (!retry && !checksumValidator.handle(e)) {
                            throw e;
                        }
                        listener.transferCorrupted(e);
                        if (retry) {
                            checksumValidator.retry();
                        } else {
                            break;
                        }
                    }
                }
                tempFile.move();
                if (persistedChecksums) {
                    checksumValidator.commit();
                }
            }
        }
    }

    class PutTaskRunner extends TaskRunner {

        private final File file;

        private final Collection<RepositoryLayout.ChecksumLocation> checksumLocations;

        PutTaskRunner(
                URI path,
                File file,
                List<RepositoryLayout.ChecksumLocation> checksumLocations,
                TransferTransportListener<?> listener) {
            super(path, listener);
            this.file = requireNonNull(file, "source file cannot be null");
            this.checksumLocations = safe(checksumLocations);
        }

        @SuppressWarnings("checkstyle:innerassignment")
        @Override
        protected void runTask() throws Exception {
            transporter.put(new PutTask(path).setDataFile(file).setListener(listener));
            uploadChecksums(file, null);
        }

        /**
         * @param file  source
         * @param bytes transformed data from file or {@code null}
         */
        private void uploadChecksums(File file, byte[] bytes) {
            if (checksumLocations.isEmpty()) {
                return;
            }
            try {
                ArrayList<ChecksumAlgorithmFactory> algorithms = new ArrayList<>();
                for (RepositoryLayout.ChecksumLocation checksumLocation : checksumLocations) {
                    algorithms.add(checksumLocation.getChecksumAlgorithmFactory());
                }

                Map<String, String> sumsByAlgo;
                if (bytes != null) {
                    sumsByAlgo = ChecksumAlgorithmHelper.calculate(bytes, algorithms);
                } else {
                    sumsByAlgo = ChecksumAlgorithmHelper.calculate(file, algorithms);
                }

                for (RepositoryLayout.ChecksumLocation checksumLocation : checksumLocations) {
                    uploadChecksum(
                            checksumLocation.getLocation(),
                            sumsByAlgo.get(checksumLocation
                                    .getChecksumAlgorithmFactory()
                                    .getName()));
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to upload checksums for {}", file, e);
                throw new UncheckedIOException(e);
            }
        }

        private void uploadChecksum(URI location, Object checksum) {
            try {
                if (checksum instanceof Exception) {
                    throw (Exception) checksum;
                }
                transporter.put(new PutTask(location).setDataString((String) checksum));
            } catch (Exception e) {
                LOGGER.warn("Failed to upload checksum to {}", location, e);
            }
        }
    }
}
