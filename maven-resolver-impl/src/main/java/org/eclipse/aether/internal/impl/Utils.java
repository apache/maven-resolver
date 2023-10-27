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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.MetadataGenerator;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.resolution.ResolutionErrorPolicyRequest;
import org.eclipse.aether.transfer.RepositoryOfflineException;

/**
 * Internal utility methods.
 */
final class Utils {

    public static PrioritizedComponents<MetadataGeneratorFactory> sortMetadataGeneratorFactories(
            RepositorySystemSession session, Map<String, MetadataGeneratorFactory> factories) {
        return PrioritizedComponents.reuseOrCreate(session, factories, MetadataGeneratorFactory::getPriority);
    }

    public static List<Metadata> prepareMetadata(
            List<? extends MetadataGenerator> generators, List<? extends Artifact> artifacts) {
        List<Metadata> metadatas = new ArrayList<>();

        for (MetadataGenerator generator : generators) {
            metadatas.addAll(generator.prepare(artifacts));
        }

        return metadatas;
    }

    public static List<Metadata> finishMetadata(
            List<? extends MetadataGenerator> generators, List<? extends Artifact> artifacts) {
        List<Metadata> metadatas = new ArrayList<>();

        for (MetadataGenerator generator : generators) {
            metadatas.addAll(generator.finish(artifacts));
        }

        return metadatas;
    }

    public static <T> List<T> combine(Collection<? extends T> first, Collection<? extends T> second) {
        List<T> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    public static int getPolicy(RepositorySystemSession session, Artifact artifact, RemoteRepository repository) {
        ResolutionErrorPolicy rep = session.getResolutionErrorPolicy();
        if (rep == null) {
            return ResolutionErrorPolicy.CACHE_DISABLED;
        }
        return rep.getArtifactPolicy(session, new ResolutionErrorPolicyRequest<>(artifact, repository));
    }

    public static int getPolicy(RepositorySystemSession session, Metadata metadata, RemoteRepository repository) {
        ResolutionErrorPolicy rep = session.getResolutionErrorPolicy();
        if (rep == null) {
            return ResolutionErrorPolicy.CACHE_DISABLED;
        }
        return rep.getMetadataPolicy(session, new ResolutionErrorPolicyRequest<>(metadata, repository));
    }

    public static void appendClassLoader(StringBuilder buffer, Object component) {
        ClassLoader loader = component.getClass().getClassLoader();
        if (loader != null && !loader.equals(Utils.class.getClassLoader())) {
            buffer.append(" from ").append(loader);
        }
    }

    public static void checkOffline(
            RepositorySystemSession session, OfflineController offlineController, RemoteRepository repository)
            throws RepositoryOfflineException {
        if (session.isOffline()) {
            offlineController.checkOffline(session, repository);
        }
    }
}
