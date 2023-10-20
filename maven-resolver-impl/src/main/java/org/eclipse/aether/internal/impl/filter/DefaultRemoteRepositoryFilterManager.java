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
package org.eclipse.aether.internal.impl.filter;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.RemoteRepositoryFilterManager;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilterSource;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link RemoteRepositoryFilterManager}, it always returns a {@link RemoteRepositoryFilter}
 * instance, even if no filter sources enabled/registered (then "always allow" instance).
 * <p>
 * The created {@link RemoteRepositoryFilter} instance is created once per session and cached.
 *
 * @since 1.9.0
 */
@Singleton
@Named
public final class DefaultRemoteRepositoryFilterManager implements RemoteRepositoryFilterManager {
    private static final String INSTANCE_KEY = DefaultRemoteRepositoryFilterManager.class.getName() + ".instance";

    private final Map<String, RemoteRepositoryFilterSource> sources;

    @Inject
    public DefaultRemoteRepositoryFilterManager(Map<String, RemoteRepositoryFilterSource> sources) {
        this.sources = requireNonNull(sources);
    }

    @Override
    public RemoteRepositoryFilter getRemoteRepositoryFilter(RepositorySystemSession session) {
        return (RemoteRepositoryFilter) session.getData().computeIfAbsent(INSTANCE_KEY, () -> {
            HashMap<String, RemoteRepositoryFilter> filters = new HashMap<>();
            for (Map.Entry<String, RemoteRepositoryFilterSource> entry : sources.entrySet()) {
                RemoteRepositoryFilter filter = entry.getValue().getRemoteRepositoryFilter(session);
                if (filter != null) {
                    filters.put(entry.getKey(), filter);
                }
            }
            if (!filters.isEmpty()) {
                return new Participants(filters);
            } else {
                return null;
            }
        });
    }

    /**
     * {@link RemoteRepositoryFilter} instance when there are participant filters present. It evaluates into result
     * using {@link Consensus}.
     */
    private static class Participants implements RemoteRepositoryFilter {
        private final Map<String, RemoteRepositoryFilter> participants;

        private Participants(Map<String, RemoteRepositoryFilter> participants) {
            this.participants = Collections.unmodifiableMap(participants);
        }

        @Override
        public RemoteRepositoryFilter.Result acceptArtifact(RemoteRepository remoteRepository, Artifact artifact) {
            return new Consensus(
                    participants.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()
                            .acceptArtifact(remoteRepository, artifact))));
        }

        @Override
        public RemoteRepositoryFilter.Result acceptMetadata(RemoteRepository remoteRepository, Metadata metadata) {
            return new Consensus(
                    participants.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()
                            .acceptMetadata(remoteRepository, metadata))));
        }
    }

    /**
     * {@link RemoteRepositoryFilter.Result} based on "consensus". All participant have to "accept" to make this
     * instance "accept".
     */
    private static class Consensus implements RemoteRepositoryFilter.Result {
        private final boolean accepted;

        private final String reasoning;

        Consensus(Map<String, RemoteRepositoryFilter.Result> results) {
            this.accepted = results.values().stream().allMatch(RemoteRepositoryFilter.Result::isAccepted);
            this.reasoning = results.values().stream()
                    .filter(r -> !r.isAccepted())
                    .map(RemoteRepositoryFilter.Result::reasoning)
                    .collect(Collectors.joining("; "));
        }

        @Override
        public boolean isAccepted() {
            return accepted;
        }

        @Override
        public String reasoning() {
            return reasoning;
        }
    }
}
