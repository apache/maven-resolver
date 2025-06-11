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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilterSource;

/**
 * Some filters used in UTs.
 */
@SuppressWarnings("checkstyle:MemberName")
public final class Filters {
    /**
     * Returns a filter that always accepts.
     */
    public static RemoteRepositoryFilterSource alwaysAccept() {
        return new RemoteRepositoryFilterSource() {
            public String getName() {
                return "always-accept";
            }

            private final RemoteRepositoryFilter.Result RESULT =
                    new RemoteRepositoryFilterSourceSupport.SimpleResult(true, getName());

            @Override
            public RemoteRepositoryFilter getRemoteRepositoryFilter(RepositorySystemSession session) {
                return new RemoteRepositoryFilter() {
                    @Override
                    public Result acceptArtifact(RemoteRepository remoteRepository, Artifact artifact) {
                        return RESULT;
                    }

                    @Override
                    public Result acceptMetadata(RemoteRepository remoteRepository, Metadata metadata) {
                        return RESULT;
                    }
                };
            }
        };
    }

    /**
     * Returns a filter that always accepts from given repo.
     */
    public static RemoteRepositoryFilterSource alwaysAcceptFrom(String repoId) {
        return new RemoteRepositoryFilterSource() {
            public String getName() {
                return "always-accept-" + repoId;
            }

            private final RemoteRepositoryFilter.Result MATCHED =
                    new RemoteRepositoryFilterSourceSupport.SimpleResult(true, getName());

            private final RemoteRepositoryFilter.Result UNMATCHED =
                    new RemoteRepositoryFilterSourceSupport.SimpleResult(false, getName());

            @Override
            public RemoteRepositoryFilter getRemoteRepositoryFilter(RepositorySystemSession session) {
                return new RemoteRepositoryFilter() {
                    @Override
                    public Result acceptArtifact(RemoteRepository remoteRepository, Artifact artifact) {
                        return repoId.equals(remoteRepository.getId()) ? MATCHED : UNMATCHED;
                    }

                    @Override
                    public Result acceptMetadata(RemoteRepository remoteRepository, Metadata metadata) {
                        return repoId.equals(remoteRepository.getId()) ? MATCHED : UNMATCHED;
                    }
                };
            }
        };
    }

    /**
     * Returns a filter that never accepts.
     */
    public static RemoteRepositoryFilterSource neverAccept() {
        return new RemoteRepositoryFilterSource() {
            public String getName() {
                return "never-accept";
            }

            private final RemoteRepositoryFilter.Result RESULT =
                    new RemoteRepositoryFilterSourceSupport.SimpleResult(false, getName());

            @Override
            public RemoteRepositoryFilter getRemoteRepositoryFilter(RepositorySystemSession session) {
                return new RemoteRepositoryFilter() {
                    @Override
                    public Result acceptArtifact(RemoteRepository remoteRepository, Artifact artifact) {
                        return RESULT;
                    }

                    @Override
                    public Result acceptMetadata(RemoteRepository remoteRepository, Metadata metadata) {
                        return RESULT;
                    }
                };
            }
        };
    }

    /**
     * Returns a filter that never accepts from given repo.
     */
    public static RemoteRepositoryFilterSource neverAcceptFrom(String repoId) {
        return new RemoteRepositoryFilterSource() {
            public String getName() {
                return "never-accept-" + repoId;
            }

            private final RemoteRepositoryFilter.Result MATCHED =
                    new RemoteRepositoryFilterSourceSupport.SimpleResult(false, getName());

            private final RemoteRepositoryFilter.Result UNMATCHED =
                    new RemoteRepositoryFilterSourceSupport.SimpleResult(true, getName());

            @Override
            public RemoteRepositoryFilter getRemoteRepositoryFilter(RepositorySystemSession session) {
                return new RemoteRepositoryFilter() {
                    @Override
                    public Result acceptArtifact(RemoteRepository remoteRepository, Artifact artifact) {
                        return repoId.equals(remoteRepository.getId()) ? MATCHED : UNMATCHED;
                    }

                    @Override
                    public Result acceptMetadata(RemoteRepository remoteRepository, Metadata metadata) {
                        return repoId.equals(remoteRepository.getId()) ? MATCHED : UNMATCHED;
                    }
                };
            }
        };
    }
}
