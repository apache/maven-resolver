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

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilterSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UT helper for {@link RemoteRepositoryFilterSource} UTs.
 */
public abstract class RemoteRepositoryFilterSourceTestSupport {
    protected final Artifact acceptedArtifact = new DefaultArtifact("org.one:aid:1.0");

    protected final Artifact notAcceptedArtifact = new DefaultArtifact("org.two:aid:1.0");

    protected DefaultRepositorySystemSession session;

    protected RemoteRepository remoteRepository;

    protected RemoteRepositoryFilterSource subject;

    @BeforeEach
    void setup() {
        remoteRepository = new RemoteRepository.Builder("test", "default", "https://irrelevant.com").build();
        session = TestUtils.newSession();
        subject = getRemoteRepositoryFilterSource(session, remoteRepository);
    }

    protected abstract RemoteRepositoryFilterSource getRemoteRepositoryFilterSource(
            DefaultRepositorySystemSession session, RemoteRepository remoteRepository);

    protected abstract void enableSource(DefaultRepositorySystemSession session, boolean enabled);

    protected abstract void setOutcome(DefaultRepositorySystemSession session, boolean enabled);

    protected abstract void allowArtifact(
            DefaultRepositorySystemSession session, RemoteRepository remoteRepository, Artifact artifact);

    @Test
    void notEnabled() {
        enableSource(session, false);
        RemoteRepositoryFilter filter = subject.getRemoteRepositoryFilter(session);
        assertNull(filter);
    }

    @Test
    void enabledNoInput() {
        enableSource(session, true);
        RemoteRepositoryFilter filter = subject.getRemoteRepositoryFilter(session);
        assertNotNull(filter);

        RemoteRepositoryFilter.Result result = filter.acceptArtifact(remoteRepository, acceptedArtifact);

        assertTrue(result.isAccepted());
        assertTrue(result.reasoning().endsWith("No input available"));
    }

    @Test
    void enabledNoInputAlteredOutcome() {
        enableSource(session, true);
        setOutcome(session, false);
        RemoteRepositoryFilter filter = subject.getRemoteRepositoryFilter(session);
        assertNotNull(filter);

        RemoteRepositoryFilter.Result result = filter.acceptArtifact(remoteRepository, acceptedArtifact);

        assertFalse(result.isAccepted());
        assertTrue(result.reasoning().endsWith("No input available"));
    }

    @Test
    void acceptedArtifact() {
        enableSource(session, true);
        allowArtifact(session, remoteRepository, acceptedArtifact);

        RemoteRepositoryFilter filter = subject.getRemoteRepositoryFilter(session);
        assertNotNull(filter);

        RemoteRepositoryFilter.Result result = filter.acceptArtifact(remoteRepository, acceptedArtifact);

        assertTrue(result.isAccepted());
        assertTrue(result.reasoning().contains("allowed from test"));
    }

    @Test
    void notAcceptedArtifact() {
        enableSource(session, true);
        allowArtifact(session, remoteRepository, acceptedArtifact);

        RemoteRepositoryFilter filter = subject.getRemoteRepositoryFilter(session);
        assertNotNull(filter);

        RemoteRepositoryFilter.Result result = filter.acceptArtifact(remoteRepository, notAcceptedArtifact);

        assertFalse(result.isAccepted());
        assertTrue(result.reasoning().contains("NOT allowed from test"));
    }
}
