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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public abstract class FileTrustedChecksumsSourceTestSupport
{
    protected static final Artifact ARTIFACT_WITHOUT_CHECKSUM = new DefaultArtifact( "test:test:1.0" );

    protected static final Artifact ARTIFACT_WITH_CHECKSUM = new DefaultArtifact( "test:test:2.0" );

    protected static final String ARTIFACT_TRUSTED_CHECKSUM = "trustedChecksum";

    protected DefaultRepositorySystemSession session;

    protected ChecksumAlgorithmFactory checksumAlgorithmFactory;

    private FileTrustedChecksumsSourceSupport subject;

    @Before
    public void before() throws Exception
    {
        session = TestUtils.newSession();
        // populate local repository
        Path basedir = session.getLocalRepository().getBasedir().toPath()
                .resolve( FileTrustedChecksumsSourceSupport.LOCAL_REPO_PREFIX_DIR );
        checksumAlgorithmFactory = new Sha1ChecksumAlgorithmFactory();
        subject = prepareSubject( basedir );
    }

    protected abstract FileTrustedChecksumsSourceSupport prepareSubject( Path basedir ) throws IOException;

    @Test
    public void noProvidedArtifactChecksum()
    {
        Map<String, String> providedChecksums = subject.getTrustedArtifactChecksums(
                session,
                ARTIFACT_WITHOUT_CHECKSUM,
                session.getLocalRepository(),
                Collections.singletonList( checksumAlgorithmFactory )
        );
        assertNull( providedChecksums );
    }

    @Test
    public void haveProvidedArtifactChecksum()
    {
        Map<String, String> providedChecksums = subject.getTrustedArtifactChecksums(
                session,
                ARTIFACT_WITH_CHECKSUM,
                session.getLocalRepository(),
                Collections.singletonList( checksumAlgorithmFactory )
        );
        assertNotNull( providedChecksums );
        assertEquals( providedChecksums.get( Sha1ChecksumAlgorithmFactory.NAME ), ARTIFACT_TRUSTED_CHECKSUM );
    }
}
