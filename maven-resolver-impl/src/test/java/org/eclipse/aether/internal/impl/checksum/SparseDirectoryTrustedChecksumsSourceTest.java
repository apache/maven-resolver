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

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.RepositorySystemLifecycle;
import org.eclipse.aether.internal.impl.DefaultFileProcessor;
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SparseDirectoryTrustedChecksumsSourceTest extends FileTrustedChecksumsSourceTestSupport {
    @Override
    protected FileTrustedChecksumsSourceSupport prepareSubject(RepositorySystemLifecycle lifecycle) {
        return new SparseDirectoryTrustedChecksumsSource(new DefaultFileProcessor(), new DefaultLocalPathComposer());
    }

    @Override
    protected void enableSource(DefaultRepositorySystemSession session) {
        session.setConfigProperty("aether.trustedChecksumsSource.sparseDirectory", Boolean.TRUE.toString());
    }

    @Test
    public void dotDotRepositoryIdIsNeutralized() throws Exception {
        DefaultRepositorySystemSession session = TestUtils.newSession();
        enableSource(session);

        SparseDirectoryTrustedChecksumsSource source =
                new SparseDirectoryTrustedChecksumsSource(new DefaultFileProcessor(), new DefaultLocalPathComposer());

        TrustedChecksumsSource.Writer writer = source.getTrustedArtifactChecksumsWriter(session);
        assertNotNull(writer);

        // a repository id of ".." must be neutralized into a harmless path segment rather than spliced
        // into the checksums basedir path as-is
        RemoteRepository dotDotRepository =
                new RemoteRepository.Builder("..", "default", "https://repo.example.org/").build();
        Artifact artifact = new DefaultArtifact("test:test:1.0");
        Sha1ChecksumAlgorithmFactory checksumAlgorithmFactory = new Sha1ChecksumAlgorithmFactory();

        writer.addTrustedArtifactChecksums(
                artifact,
                dotDotRepository,
                singletonList(checksumAlgorithmFactory),
                singletonMap(checksumAlgorithmFactory.getName(), "000"));

        Path checksumsBasedir =
                session.getLocalRepository().getBasedir().toPath().resolve(".checksums");
        assertTrue(Files.isRegularFile(checksumsBasedir.resolve("-DOTDOT-/test/test/1.0/test-1.0.jar.sha1")));
        assertFalse(Files.exists(checksumsBasedir.getParent().resolve("test/test/1.0/test-1.0.jar.sha1")));
    }
}
