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
package org.eclipse.aether.internal.impl.checksum;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TrustedToProvidedChecksumsSourceAdapterTest {
    private final Artifact artifactWithChecksum = new DefaultArtifact("g:a:v1");

    private final Artifact artifactWithoutChecksum = new DefaultArtifact("g:a:v2");

    private final RemoteRepository repository =
            new RemoteRepository.Builder("repo", "default", "https://example.com").build();

    private final List<ChecksumAlgorithmFactory> checksums =
            Collections.singletonList(new Sha1ChecksumAlgorithmFactory());

    private final RepositorySystemSession session = mock(RepositorySystemSession.class);

    private final TrustedChecksumsSource trustedChecksumsSource = mock(TrustedChecksumsSource.class);

    private TrustedToProvidedChecksumsSourceAdapter adapter;

    @BeforeEach
    void before() {
        HashMap<String, String> result = new HashMap<>();
        result.put(Sha1ChecksumAlgorithmFactory.NAME, "foo");
        when(trustedChecksumsSource.getTrustedArtifactChecksums(
                        eq(session), eq(artifactWithChecksum), eq(repository), eq(checksums)))
                .thenReturn(result);
        adapter = new TrustedToProvidedChecksumsSourceAdapter(
                Collections.singletonMap("trusted", trustedChecksumsSource));
    }

    @Test
    void testSimplePositive() {
        ArtifactDownload transfer = new ArtifactDownload();
        transfer.setArtifact(artifactWithChecksum);
        Map<String, String> chk = adapter.getProvidedArtifactChecksums(session, transfer, repository, checksums);
        assertNotNull(chk);
        assertEquals(chk.get(Sha1ChecksumAlgorithmFactory.NAME), "foo");
    }

    @Test
    void testSimpleNegative() {
        ArtifactDownload transfer = new ArtifactDownload();
        transfer.setArtifact(artifactWithoutChecksum);
        Map<String, String> chk = adapter.getProvidedArtifactChecksums(session, transfer, repository, checksums);
        assertNull(chk);
    }

    @Test
    void testMrmPositive() {
        RemoteRepository mrm = new RemoteRepository.Builder("mrm", "default", "https://example.com").build();
        ArtifactDownload transfer = new ArtifactDownload();
        transfer.setArtifact(artifactWithChecksum);
        transfer.setRepositories(Collections.singletonList(repository));
        Map<String, String> chk = adapter.getProvidedArtifactChecksums(session, transfer, mrm, checksums);
        assertNotNull(chk);
        assertEquals(chk.get(Sha1ChecksumAlgorithmFactory.NAME), "foo");
    }

    @Test
    void testMrmNegative() {
        RemoteRepository mrm = new RemoteRepository.Builder("mrm", "default", "https://example.com").build();
        ArtifactDownload transfer = new ArtifactDownload();
        transfer.setArtifact(artifactWithoutChecksum);
        transfer.setRepositories(Collections.singletonList(repository));
        Map<String, String> chk = adapter.getProvidedArtifactChecksums(session, transfer, mrm, checksums);
        assertNull(chk);
    }
}
