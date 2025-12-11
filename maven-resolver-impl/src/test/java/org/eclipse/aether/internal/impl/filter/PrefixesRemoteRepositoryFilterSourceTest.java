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
import java.util.List;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.internal.impl.DefaultArtifactPredicateFactory;
import org.eclipse.aether.internal.impl.DefaultRepositoryKeyFunctionFactory;
import org.eclipse.aether.internal.impl.DefaultRepositoryLayoutProvider;
import org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilterSource;
import org.junit.jupiter.api.Test;

import static org.eclipse.aether.internal.impl.checksum.Checksums.checksumsSelector;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        RemoteRepositoryManager remoteRepositoryManager = new RemoteRepositoryManager() {
            @Override
            public List<RemoteRepository> aggregateRepositories(
                    RepositorySystemSession session,
                    List<RemoteRepository> dominantRepositories,
                    List<RemoteRepository> recessiveRepositories,
                    boolean recessiveIsRaw) {
                return recessiveRepositories;
            }

            @Override
            public RepositoryPolicy getPolicy(
                    RepositorySystemSession session, RemoteRepository repository, boolean releases, boolean snapshots) {
                throw new UnsupportedOperationException("not implemented");
            }
        };
        when(metadataResolver.resolveMetadata(any(RepositorySystemSession.class), any(Collection.class)))
                .thenThrow(new IllegalStateException("should not enter here"));
        DefaultRepositoryLayoutProvider layoutProvider = new DefaultRepositoryLayoutProvider(Collections.singletonMap(
                Maven2RepositoryLayoutFactory.NAME,
                new Maven2RepositoryLayoutFactory(
                        checksumsSelector(), new DefaultArtifactPredicateFactory(checksumsSelector()))));
        return new PrefixesRemoteRepositoryFilterSource(
                new DefaultRepositoryKeyFunctionFactory(),
                () -> metadataResolver,
                () -> remoteRepositoryManager,
                layoutProvider);
    }

    @Override
    protected void enableSource(DefaultRepositorySystemSession session, boolean enabled) {
        // disable resolving/auto discovery
        session.setConfigProperty(
                "aether.remoteRepositoryFilter." + PrefixesRemoteRepositoryFilterSource.NAME + ".resolvePrefixFiles",
                Boolean.valueOf(false).toString());
        session.setConfigProperty(
                "aether.remoteRepositoryFilter." + PrefixesRemoteRepositoryFilterSource.NAME,
                Boolean.valueOf(enabled).toString());
    }

    @Override
    protected void setOutcome(DefaultRepositorySystemSession session, boolean outcome) {
        session.setConfigProperty(
                "aether.remoteRepositoryFilter." + PrefixesRemoteRepositoryFilterSource.NAME + ".noInputOutcome",
                Boolean.valueOf(outcome).toString());
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
                    ("## repository-prefixes/2.0\n" + artifact.getGroupId().replaceAll("\\.", "/"))
                            .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void notAcceptedArtifactFromMirror() {
        RemoteRepository mirror = new RemoteRepository.Builder("mirror", "default", "https://irrelevant.com")
                .addMirroredRepository(remoteRepository)
                .build();
        enableSource(session, true);

        RemoteRepositoryFilter filter = subject.getRemoteRepositoryFilter(session);
        assertNotNull(filter);

        RemoteRepositoryFilter.Result result = filter.acceptArtifact(mirror, acceptedArtifact);

        assertTrue(result.isAccepted());
        assertEquals("prefixes: No input available", result.reasoning());
    }
}
