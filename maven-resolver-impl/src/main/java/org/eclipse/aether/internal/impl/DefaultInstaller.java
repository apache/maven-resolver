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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.Installer;
import org.eclipse.aether.impl.MetadataGenerator;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.metadata.MergeableMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.spi.artifact.generator.ArtifactGenerator;
import org.eclipse.aether.spi.artifact.generator.ArtifactGeneratorFactory;
import org.eclipse.aether.spi.artifact.transformer.ArtifactTransformer;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 */
@Singleton
@Named
public class DefaultInstaller implements Installer {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PathProcessor pathProcessor;

    private final RepositoryEventDispatcher repositoryEventDispatcher;

    private final Map<String, ArtifactGeneratorFactory> artifactFactories;

    private final Map<String, MetadataGeneratorFactory> metadataFactories;

    private final Map<String, ArtifactTransformer> artifactTransformers;

    private final SyncContextFactory syncContextFactory;

    @Inject
    public DefaultInstaller(
            PathProcessor pathProcessor,
            RepositoryEventDispatcher repositoryEventDispatcher,
            Map<String, ArtifactGeneratorFactory> artifactFactories,
            Map<String, MetadataGeneratorFactory> metadataFactories,
            Map<String, ArtifactTransformer> artifactTransformers,
            SyncContextFactory syncContextFactory) {
        this.pathProcessor = requireNonNull(pathProcessor, "path processor cannot be null");
        this.repositoryEventDispatcher =
                requireNonNull(repositoryEventDispatcher, "repository event dispatcher cannot be null");
        this.artifactFactories = Collections.unmodifiableMap(artifactFactories);
        this.metadataFactories = Collections.unmodifiableMap(metadataFactories);
        this.artifactTransformers = Collections.unmodifiableMap(artifactTransformers);
        this.syncContextFactory = requireNonNull(syncContextFactory, "sync context factory cannot be null");
    }

    @Override
    public InstallResult install(RepositorySystemSession session, InstallRequest request) throws InstallationException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");
        for (ArtifactTransformer transformer : artifactTransformers.values()) {
            request = transformer.transformInstallArtifacts(session, request);
        }
        try (SyncContext syncContext = syncContextFactory.newInstance(session, false)) {
            return install(syncContext, session, request);
        }
    }

    private InstallResult install(SyncContext syncContext, RepositorySystemSession session, InstallRequest request)
            throws InstallationException {
        InstallResult result = new InstallResult(request);

        RequestTrace trace = RequestTrace.newChild(request.getTrace(), request);

        List<Artifact> artifacts = new ArrayList<>(request.getArtifacts());
        List<? extends ArtifactGenerator> artifactGenerators =
                Utils.getArtifactGenerators(session, artifactFactories, request);
        try {
            List<Artifact> generatedArtifacts = new ArrayList<>();
            for (ArtifactGenerator artifactGenerator : artifactGenerators) {
                Collection<? extends Artifact> generated = artifactGenerator.generate(generatedArtifacts);
                for (Artifact generatedArtifact : generated) {
                    Map<String, String> properties = new HashMap<>(generatedArtifact.getProperties());
                    properties.put(
                            ArtifactGeneratorFactory.ARTIFACT_GENERATOR_ID,
                            requireNonNull(artifactGenerator.generatorId(), "generatorId"));
                    Artifact ga = generatedArtifact.setProperties(properties);
                    generatedArtifacts.add(ga);
                }
            }
            artifacts.addAll(generatedArtifacts);

            List<? extends MetadataGenerator> metadataGenerators =
                    Utils.getMetadataGenerators(session, metadataFactories, request);

            IdentityHashMap<Metadata, Object> processedMetadata = new IdentityHashMap<>();

            List<Metadata> metadatas = Utils.prepareMetadata(metadataGenerators, artifacts);

            syncContext.acquire(artifacts, Utils.combine(request.getMetadata(), metadatas));

            for (Metadata metadata : metadatas) {
                install(session, trace, metadata);
                processedMetadata.put(metadata, null);
                result.addMetadata(metadata);
            }

            for (ListIterator<Artifact> iterator = artifacts.listIterator(); iterator.hasNext(); ) {
                Artifact artifact = iterator.next();

                for (MetadataGenerator generator : metadataGenerators) {
                    artifact = generator.transformArtifact(artifact);
                }

                iterator.set(artifact);

                install(session, trace, artifact);
                if (artifact.getProperty(ArtifactGeneratorFactory.ARTIFACT_GENERATOR_ID, null) == null) {
                    result.addArtifact(artifact);
                }
            }

            metadatas = Utils.finishMetadata(metadataGenerators, artifacts);

            syncContext.acquire(null, metadatas);

            for (Metadata metadata : metadatas) {
                install(session, trace, metadata);
                processedMetadata.put(metadata, null);
                result.addMetadata(metadata);
            }

            for (Metadata metadata : request.getMetadata()) {
                if (!processedMetadata.containsKey(metadata)) {
                    install(session, trace, metadata);
                    result.addMetadata(metadata);
                }
            }

            return result;
        } finally {
            for (ArtifactGenerator artifactGenerator : artifactGenerators) {
                try {
                    artifactGenerator.close();
                } catch (Exception e) {
                    logger.warn("ArtifactGenerator close failure: {}", artifactGenerator.generatorId(), e);
                }
            }
        }
    }

    private void install(RepositorySystemSession session, RequestTrace trace, Artifact artifact)
            throws InstallationException {
        final LocalRepositoryManager lrm = session.getLocalRepositoryManager();
        final Path srcPath = artifact.getPath();
        final Path dstPath = lrm.getRepository().getBasePath().resolve(lrm.getPathForLocalArtifact(artifact));

        artifactInstalling(session, trace, artifact, dstPath);

        Exception exception = null;
        try {
            if (dstPath.equals(srcPath)) {
                throw new IllegalStateException("cannot install " + dstPath + " to same path");
            }

            pathProcessor.copyWithTimestamp(srcPath, dstPath);
            lrm.add(session, new LocalArtifactRegistration(artifact));
        } catch (Exception e) {
            exception = e;
            throw new InstallationException("Failed to install artifact " + artifact + ": " + e.getMessage(), e);
        } finally {
            artifactInstalled(session, trace, artifact, dstPath, exception);
        }
    }

    private void install(RepositorySystemSession session, RequestTrace trace, Metadata metadata)
            throws InstallationException {
        LocalRepositoryManager lrm = session.getLocalRepositoryManager();

        Path dstPath = lrm.getRepository().getBasePath().resolve(lrm.getPathForLocalMetadata(metadata));

        metadataInstalling(session, trace, metadata, dstPath);

        Exception exception = null;
        try {
            if (metadata instanceof MergeableMetadata) {
                ((MergeableMetadata) metadata).merge(dstPath, dstPath);
            } else {
                if (dstPath.equals(metadata.getPath())) {
                    throw new IllegalStateException("cannot install " + dstPath + " to same path");
                }
                pathProcessor.copy(metadata.getPath(), dstPath);
            }

            lrm.add(session, new LocalMetadataRegistration(metadata));
        } catch (Exception e) {
            exception = e;
            throw new InstallationException("Failed to install metadata " + metadata + ": " + e.getMessage(), e);
        } finally {
            metadataInstalled(session, trace, metadata, dstPath, exception);
        }
    }

    private void artifactInstalling(
            RepositorySystemSession session, RequestTrace trace, Artifact artifact, Path dstPath) {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.ARTIFACT_INSTALLING);
        event.setTrace(trace);
        event.setArtifact(artifact);
        event.setRepository(session.getLocalRepositoryManager().getRepository());
        event.setPath(dstPath);

        repositoryEventDispatcher.dispatch(event.build());
    }

    private void artifactInstalled(
            RepositorySystemSession session, RequestTrace trace, Artifact artifact, Path dstPath, Exception exception) {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.ARTIFACT_INSTALLED);
        event.setTrace(trace);
        event.setArtifact(artifact);
        event.setRepository(session.getLocalRepositoryManager().getRepository());
        event.setPath(dstPath);
        event.setException(exception);

        repositoryEventDispatcher.dispatch(event.build());
    }

    private void metadataInstalling(
            RepositorySystemSession session, RequestTrace trace, Metadata metadata, Path dstPath) {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.METADATA_INSTALLING);
        event.setTrace(trace);
        event.setMetadata(metadata);
        event.setRepository(session.getLocalRepositoryManager().getRepository());
        event.setPath(dstPath);

        repositoryEventDispatcher.dispatch(event.build());
    }

    private void metadataInstalled(
            RepositorySystemSession session, RequestTrace trace, Metadata metadata, Path dstPath, Exception exception) {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder(session, EventType.METADATA_INSTALLED);
        event.setTrace(trace);
        event.setMetadata(metadata);
        event.setRepository(session.getLocalRepositoryManager().getRepository());
        event.setPath(dstPath);
        event.setException(exception);

        repositoryEventDispatcher.dispatch(event.build());
    }
}
