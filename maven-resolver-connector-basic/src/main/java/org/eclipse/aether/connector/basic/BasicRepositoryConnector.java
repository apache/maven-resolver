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

import static java.util.Objects.requireNonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmHelper;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.connector.checksum.ProvidedChecksumsSource;
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
import org.eclipse.aether.transform.FileTransformer;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.concurrency.RunnableErrorForwarder;
import org.eclipse.aether.util.concurrency.WorkerThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
final class BasicRepositoryConnector implements RepositoryConnector {

    private static final String CONFIG_PROP_THREADS = "aether.connector.basic.threads";

    private static final String CONFIG_PROP_RESUME = "aether.connector.resumeDownloads";

    private static final String CONFIG_PROP_RESUME_THRESHOLD = "aether.connector.resumeThreshold";

    private static final String CONFIG_PROP_SMART_CHECKSUMS = "aether.connector.smartChecksums";

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicRepositoryConnector.class);

    private final Map<String, ProvidedChecksumsSource> providedChecksumsSources;

    private final FileProcessor fileProcessor;

    private final RemoteRepository repository;

    private final RepositorySystemSession session;

    private final Transporter transporter;

    private final RepositoryLayout layout;

    private final ChecksumPolicyProvider checksumPolicyProvider;

    private final PartialFile.Factory partialFileFactory;

    private final int maxThreads;

    private final boolean smartChecksums;

    private final boolean persistedChecksums;

    private Executor executor;

    private boolean closed;

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

        maxThreads = ConfigUtils.getInteger(session, 5, CONFIG_PROP_THREADS, "maven.artifact.threads");
        smartChecksums = ConfigUtils.getBoolean(session, true, CONFIG_PROP_SMART_CHECKSUMS);
        persistedChecksums = ConfigUtils.getBoolean(
                session,
                ConfigurationProperties.DEFAULT_PERSISTED_CHECKSUMS,
                ConfigurationProperties.PERSISTED_CHECKSUMS);

        boolean resumeDownloads = ConfigUtils.getBoolean(
                session, true, CONFIG_PROP_RESUME + '.' + repository.getId(), CONFIG_PROP_RESUME);
        long resumeThreshold = ConfigUtils.getLong(
                session,
                64 * 1024,
                CONFIG_PROP_RESUME_THRESHOLD + '.' + repository.getId(),
                CONFIG_PROP_RESUME_THRESHOLD);
        int requestTimeout = ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT,
                ConfigurationProperties.REQUEST_TIMEOUT + '.' + repository.getId(),
                ConfigurationProperties.REQUEST_TIMEOUT);
        partialFileFactory = new PartialFile.Factory(resumeDownloads, resumeThreshold, requestTimeout);
    }

    private Executor getExecutor(Collection<?> artifacts, Collection<?> metadatas) {
        if (maxThreads <= 1) {
            return DirectExecutor.INSTANCE;
        }
        int tasks = safe(artifacts).size() + safe(metadatas).size();
        if (tasks <= 1) {
            return DirectExecutor.INSTANCE;
        }
        if (executor == null) {
            executor = new ThreadPoolExecutor(
                    maxThreads,
                    maxThreads,
                    3L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    new WorkerThreadFactory(getClass().getSimpleName() + '-' + repository.getHost() + '-'));
        }
        return executor;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (executor instanceof ExecutorService) {
                ((ExecutorService) executor).shutdown();
            }
            transporter.close();
        }
    }

    @Override
    public void get(
            Collection<? extends ArtifactDownload> artifactDownloads,
            Collection<? extends MetadataDownload> metadataDownloads) {
        if (closed) {
            throw new IllegalStateException("connector closed");
        }

        Executor executor = getExecutor(artifactDownloads, metadataDownloads);
        RunnableErrorForwarder errorForwarder = new RunnableErrorForwarder();
        List<ChecksumAlgorithmFactory> checksumAlgorithmFactories = layout.getChecksumAlgorithmFactories();

        for (MetadataDownload transfer : safe(metadataDownloads)) {
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
            executor.execute(errorForwarder.wrap(task));
        }

        for (ArtifactDownload transfer : safe(artifactDownloads)) {
            Map<String, String> providedChecksums = Collections.emptyMap();
            for (ProvidedChecksumsSource providedChecksumsSource : providedChecksumsSources.values()) {
                Map<String, String> provided = providedChecksumsSource.getProvidedArtifactChecksums(
                        session, transfer, checksumAlgorithmFactories);

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
            executor.execute(errorForwarder.wrap(task));
        }

        errorForwarder.await();
    }

    @Override
    public void put(
            Collection<? extends ArtifactUpload> artifactUploads,
            Collection<? extends MetadataUpload> metadataUploads) {
        if (closed) {
            throw new IllegalStateException("connector closed");
        }

        for (ArtifactUpload transfer : safe(artifactUploads)) {
            URI location = layout.getLocation(transfer.getArtifact(), true);

            TransferResource resource = newTransferResource(location, transfer.getFile(), transfer.getTrace());
            TransferEvent.Builder builder = newEventBuilder(resource, true, false);
            ArtifactTransportListener listener = new ArtifactTransportListener(transfer, repository, builder);

            List<RepositoryLayout.ChecksumLocation> checksumLocations =
                    layout.getChecksumLocations(transfer.getArtifact(), true, location);

            Runnable task = new PutTaskRunner(
                    location, transfer.getFile(), transfer.getFileTransformer(), checksumLocations, listener);
            task.run();
        }

        for (MetadataUpload transfer : safe(metadataUploads)) {
            URI location = layout.getLocation(transfer.getMetadata(), true);

            TransferResource resource = newTransferResource(location, transfer.getFile(), transfer.getTrace());
            TransferEvent.Builder builder = newEventBuilder(resource, true, false);
            MetadataTransportListener listener = new MetadataTransportListener(transfer, repository, builder);

            List<RepositoryLayout.ChecksumLocation> checksumLocations =
                    layout.getChecksumLocations(transfer.getMetadata(), true, location);

            Runnable task = new PutTaskRunner(location, transfer.getFile(), checksumLocations, listener);
            task.run();
        }
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

    class GetTaskRunner extends TaskRunner
            implements PartialFile.RemoteAccessChecker, ChecksumValidator.ChecksumFetcher {

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
        public void checkRemoteAccess() throws Exception {
            transporter.peek(new PeekTask(path));
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
            fileProcessor.mkdirs(file.getParentFile());

            PartialFile partFile = partialFileFactory.newInstance(file, this);
            if (partFile == null) {
                LOGGER.debug("Concurrent download of {} just finished, skipping download", file);
                return;
            }

            try {
                File tmp = partFile.getFile();
                listener.setChecksumCalculator(checksumValidator.newChecksumCalculator(tmp));
                for (int firstTrial = 0, lastTrial = 1, trial = firstTrial; ; trial++) {
                    boolean resume = partFile.isResume() && trial <= firstTrial;
                    GetTask task = new GetTask(path).setDataFile(tmp, resume).setListener(listener);
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
                fileProcessor.move(tmp, file);
                if (persistedChecksums) {
                    checksumValidator.commit();
                }
            } finally {
                partFile.close();
                checksumValidator.close();
            }
        }
    }

    class PutTaskRunner extends TaskRunner {

        private final File file;

        private final FileTransformer fileTransformer;

        private final Collection<RepositoryLayout.ChecksumLocation> checksumLocations;

        PutTaskRunner(
                URI path,
                File file,
                List<RepositoryLayout.ChecksumLocation> checksumLocations,
                TransferTransportListener<?> listener) {
            this(path, file, null, checksumLocations, listener);
        }

        /**
         * <strong>IMPORTANT</strong> When using a fileTransformer, the content of the file is stored in memory to
         * ensure that file content and checksums stay in sync!
         *
         * @param path
         * @param file
         * @param fileTransformer
         * @param checksumLocations
         * @param listener
         */
        PutTaskRunner(
                URI path,
                File file,
                FileTransformer fileTransformer,
                List<RepositoryLayout.ChecksumLocation> checksumLocations,
                TransferTransportListener<?> listener) {
            super(path, listener);
            this.file = requireNonNull(file, "source file cannot be null");
            this.fileTransformer = fileTransformer;
            this.checksumLocations = safe(checksumLocations);
        }

        @SuppressWarnings("checkstyle:innerassignment")
        @Override
        protected void runTask() throws Exception {
            if (fileTransformer != null) {
                // transform data once to byte array, ensure constant data for checksum
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];

                try (InputStream transformData = fileTransformer.transformData(file)) {
                    for (int read; (read = transformData.read(buffer, 0, buffer.length)) != -1; ) {
                        baos.write(buffer, 0, read);
                    }
                }

                byte[] bytes = baos.toByteArray();
                transporter.put(new PutTask(path).setDataBytes(bytes).setListener(listener));
                uploadChecksums(file, bytes);
            } else {
                transporter.put(new PutTask(path).setDataFile(file).setListener(listener));
                uploadChecksums(file, null);
            }
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

    private static class DirectExecutor implements Executor {

        static final Executor INSTANCE = new DirectExecutor();

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
