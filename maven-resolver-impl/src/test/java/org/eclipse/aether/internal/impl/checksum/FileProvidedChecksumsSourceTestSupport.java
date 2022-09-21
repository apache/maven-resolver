package org.eclipse.aether.internal.impl.checksum;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public abstract class FileProvidedChecksumsSourceTestSupport
{
    protected static final Artifact ARTIFACT_WITHOUT_CHECKSUM = new DefaultArtifact( "test:test:1.0" );

    protected static final Artifact ARTIFACT_WITH_CHECKSUM = new DefaultArtifact( "test:test:2.0" );

    protected static final String ARTIFACT_PROVIDED_CHECKSUM = "providedChecksum";

    protected DefaultRepositorySystemSession session;

    protected RepositoryLayout repositoryLayout;

    private FileProvidedChecksumsSourceSupport subject;

    @Before
    public void before() throws Exception
    {
        RemoteRepository repository =
                new RemoteRepository.Builder( "test", "default", "https://irrelevant.com" ).build();
        session = TestUtils.newSession();
        repositoryLayout = new Maven2RepositoryLayoutFactory().newInstance( session, repository );
        // populate local repository
        Path baseDir = session.getLocalRepository().getBasedir().toPath()
                .resolve( FileProvidedChecksumsSourceSupport.LOCAL_REPO_PREFIX );
        subject = prepareSubject( baseDir );
    }

    protected abstract FileProvidedChecksumsSourceSupport prepareSubject( Path baseDir ) throws IOException;

    @Test
    public void noProvidedArtifactChecksum()
    {
        ArtifactDownload transfer = new ArtifactDownload(
                ARTIFACT_WITHOUT_CHECKSUM,
                "irrelevant",
                new File( "irrelevant" ),
                RepositoryPolicy.CHECKSUM_POLICY_FAIL
        );
        Map<String, String> providedChecksums = subject.getProvidedArtifactChecksums(
                session,
                transfer,
                repositoryLayout.getChecksumAlgorithmFactories()
        );
        assertNull( providedChecksums );
    }

    @Test
    public void haveProvidedArtifactChecksum()
    {
        ArtifactDownload transfer = new ArtifactDownload(
                ARTIFACT_WITH_CHECKSUM,
                "irrelevant",
                new File( "irrelevant" ),
                RepositoryPolicy.CHECKSUM_POLICY_FAIL
        );
        Map<String, String> providedChecksums = subject.getProvidedArtifactChecksums(
                session,
                transfer,
                repositoryLayout.getChecksumAlgorithmFactories()
        );
        assertNotNull( providedChecksums );
        assertEquals( providedChecksums.get( Sha1ChecksumAlgorithmFactory.NAME ), ARTIFACT_PROVIDED_CHECKSUM );
    }
}
