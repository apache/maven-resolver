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

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.function.Function;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.RepositoryIdHelper;

/**
 * Default local path prefix composer factory: it fully reuses {@link LocalPathPrefixComposerFactorySupport} class
 * without changing anything from it.
 *
 * @since 1.8.1
 */
@Singleton
@Named
public final class DefaultLocalPathPrefixComposerFactory extends LocalPathPrefixComposerFactorySupport {
    @Override
    public LocalPathPrefixComposer createComposer(RepositorySystemSession session) {
        return new DefaultLocalPathPrefixComposer(
                isSplit(session),
                getLocalPrefix(session),
                isSplitLocal(session),
                getRemotePrefix(session),
                isSplitRemote(session),
                isSplitRemoteRepository(session),
                isSplitRemoteRepositoryLast(session),
                getReleasesPrefix(session),
                getSnapshotsPrefix(session),
                RepositoryIdHelper.cachedIdToPathSegment(session));
    }

    /**
     * {@link LocalPathPrefixComposer} implementation that fully reuses {@link LocalPathPrefixComposerSupport} class.
     */
    private static class DefaultLocalPathPrefixComposer extends LocalPathPrefixComposerSupport {
        @SuppressWarnings("checkstyle:parameternumber")
        private DefaultLocalPathPrefixComposer(
                boolean split,
                String localPrefix,
                boolean splitLocal,
                String remotePrefix,
                boolean splitRemote,
                boolean splitRemoteRepository,
                boolean splitRemoteRepositoryLast,
                String releasesPrefix,
                String snapshotsPrefix,
                Function<RemoteRepository, String> idToPathSegmentFunction) {
            super(
                    split,
                    localPrefix,
                    splitLocal,
                    remotePrefix,
                    splitRemote,
                    splitRemoteRepository,
                    splitRemoteRepositoryLast,
                    releasesPrefix,
                    snapshotsPrefix,
                    idToPathSegmentFunction);
        }
    }
}
