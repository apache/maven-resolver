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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.internal.impl.DefaultArtifactPredicateFactory;
import org.eclipse.aether.internal.impl.DefaultRepositoryLayoutProvider;
import org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilterSource;

import static org.eclipse.aether.internal.impl.checksum.Checksums.checksumsSelector;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UT for {@link PrefixesRemoteRepositoryFilterSource}.
 */
public class PrefixesRemoteRepositoryFilterSourceTest extends RemoteRepositoryFilterSourceTestSupport {
    @Override
    protected RemoteRepositoryFilterSource getRemoteRepositoryFilterSource(
            DefaultRepositorySystemSession session, RemoteRepository remoteRepository) {
        // in test we do not resolve; just reply failed resolution
        MetadataResult failed = new MetadataResult(new MetadataRequest());
        MetadataResolver metadataResolver = mock(MetadataResolver.class);
        when(metadataResolver.resolveMetadata(any(RepositorySystemSession.class), any(Collection.class)))
                .thenReturn(Collections.singletonList(failed));
        DefaultRepositoryLayoutProvider layoutProvider = new DefaultRepositoryLayoutProvider(Collections.singletonMap(
                Maven2RepositoryLayoutFactory.NAME,
                new Maven2RepositoryLayoutFactory(
                        checksumsSelector(), new DefaultArtifactPredicateFactory(checksumsSelector()))));
        return new PrefixesRemoteRepositoryFilterSource(() -> metadataResolver, layoutProvider);
    }

    @Override
    protected void enableSource(DefaultRepositorySystemSession session, boolean enabled) {
        session.setConfigProperty(
                "aether.remoteRepositoryFilter." + PrefixesRemoteRepositoryFilterSource.NAME,
                Boolean.valueOf(enabled).toString());
    }

    @Override
    protected void allowArtifact(
            DefaultRepositorySystemSession session, RemoteRepository remoteRepository, Artifact artifact) {
        try {
            Path baseDir = session.getLocalRepository()
                    .getBasePath()
                    .resolve(PrefixesRemoteRepositoryFilterSource.LOCAL_REPO_PREFIX_DIR);
            Path prefixes = baseDir.resolve(PrefixesRemoteRepositoryFilterSource.PREFIXES_FILE_PREFIX
                    + remoteRepository.getId()
                    + PrefixesRemoteRepositoryFilterSource.PREFIXES_FILE_SUFFIX);
            Files.createDirectories(prefixes.getParent());
            Files.write(
                    prefixes,
                    (PrefixesRemoteRepositoryFilterSource.PREFIX_FIRST_LINE + "\n"
                                    + artifact.getGroupId().replaceAll("\\.", "/"))
                            .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
