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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.RepositorySystemLifecycle;
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer;
import org.eclipse.aether.internal.impl.DefaultRepositoryKeyFunctionFactory;
import org.eclipse.aether.internal.impl.DefaultRepositorySystemLifecycle;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.eclipse.aether.spi.io.PathProcessorSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

public class SummaryFileTrustedChecksumsSourceTest extends FileTrustedChecksumsSourceTestSupport {
    @Override
    protected FileTrustedChecksumsSourceSupport prepareSubject(RepositorySystemLifecycle lifecycle) {
        return new SummaryFileTrustedChecksumsSource(
                new DefaultRepositoryKeyFunctionFactory(),
                new DefaultLocalPathComposer(),
                lifecycle,
                new PathProcessorSupport());
    }

    @Override
    protected void enableSource(DefaultRepositorySystemSession session) {
        session.setConfigProperty("aether.trustedChecksumsSource.summaryFile", Boolean.TRUE.toString());
    }

    @Test
    void ensureOrderIsHumanFriendly(@TempDir final Path work) throws NoLocalRepositoryManagerException, IOException {
        final RepositorySystemLifecycle lifecycle = new DefaultRepositorySystemLifecycle();
        final FileTrustedChecksumsSourceSupport src = prepareSubject(lifecycle);
        final DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(h -> false);
        final LocalRepository repository = new LocalRepository(work.toFile(), "simple");
        session.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory().newInstance(session, repository));

        final Sha1ChecksumAlgorithmFactory algorithmFactory = new Sha1ChecksumAlgorithmFactory();
        final TrustedChecksumsSource.Writer appender = src.doGetTrustedArtifactChecksumsWriter(session);

        // we ensure if we sort by checksum we keep the "add" order
        // to avoid false positives but we'll sort by paths so not keep it
        appender.addTrustedArtifactChecksums(
                new DefaultArtifact("org.foo", "bar", "jar", "1.2.3"),
                repository,
                singletonList(algorithmFactory),
                singletonMap("SHA-1", "000"));
        appender.addTrustedArtifactChecksums(
                new DefaultArtifact("com.dummy", "something", "jar", "0.0.1"),
                repository,
                singletonList(algorithmFactory),
                singletonMap("SHA-1", "111"));
        appender.addTrustedArtifactChecksums(
                new DefaultArtifact("org.zzzz", "art", "jar", "5.6.7"),
                repository,
                singletonList(algorithmFactory),
                singletonMap("SHA-1", "222"));

        // generate the dump
        lifecycle.systemEnded();

        // ensure it is sorted by artifact "path"
        assertLinesMatch(
                asList(
                        "111  com/dummy/something/0.0.1/something-0.0.1.jar",
                        "000  org/foo/bar/1.2.3/bar-1.2.3.jar",
                        "222  org/zzzz/art/5.6.7/art-5.6.7.jar"),
                Files.readAllLines(work.resolve(".checksums/checksums-local.sha1")));
    }
}
