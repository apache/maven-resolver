package org.eclipse.aether.internal.impl.resolution;

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
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.impl.checksum.Sha1ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySelector;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * UT for {@link TrustedChecksumsArtifactResolverPostProcessor}.
 */
public class TrustedChecksumsArtifactResolverPostProcessorTest implements TrustedChecksumsSource
{
    private static final String TRUSTED_SOURCE_NAME = "test";

    private Artifact artifactWithoutTrustedChecksum;

    private Artifact artifactWithTrustedChecksum;

    private String artifactTrustedChecksum;

    protected DefaultRepositorySystemSession session;

    protected ChecksumAlgorithmFactory checksumAlgorithmFactory = new Sha1ChecksumAlgorithmFactory();

    private TrustedChecksumsArtifactResolverPostProcessor subject;

    private TrustedChecksumsSource.Writer trustedChecksumsWriter;

    @Before
    public void prepareSubject() throws IOException
    {
        // make the two artifacts, BOTH as resolved
        File tmp = Files.createTempFile( "artifact", "tmp" ).toFile();
        artifactWithoutTrustedChecksum = new DefaultArtifact( "test:test:1.0" ).setFile( tmp );
        artifactWithTrustedChecksum = new DefaultArtifact( "test:test:2.0" ).setFile( tmp );
        artifactTrustedChecksum = "da39a3ee5e6b4b0d3255bfef95601890afd80709"; // empty file

        session = TestUtils.newSession();
        ChecksumAlgorithmFactorySelector selector = new ChecksumAlgorithmFactorySelector()
        {
            @Override
            public ChecksumAlgorithmFactory select( String algorithmName )
            {
                if ( checksumAlgorithmFactory.getName().equals( algorithmName ) )
                {
                    return checksumAlgorithmFactory;
                }
                throw new IllegalArgumentException("no alg factory for " + algorithmName);
            }

            @Override
            public Collection<ChecksumAlgorithmFactory> getChecksumAlgorithmFactories()
            {
                return Collections.singletonList( checksumAlgorithmFactory );
            }
        };
        subject = new TrustedChecksumsArtifactResolverPostProcessor( selector,
                Collections.singletonMap( TRUSTED_SOURCE_NAME, this ) );
        trustedChecksumsWriter = null;
        session.setConfigProperty( "aether.artifactResolver.postProcessor.trustedChecksums", Boolean.TRUE.toString() );
    }

    // -- TrustedChecksumsSource interface BEGIN

    @Override
    public Map<String, String> getTrustedArtifactChecksums( RepositorySystemSession session, Artifact artifact,
                                                            ArtifactRepository artifactRepository,
                                                            List<ChecksumAlgorithmFactory> checksumAlgorithmFactories )
    {
        if ( ArtifactIdUtils.toId( artifactWithTrustedChecksum ).equals( ArtifactIdUtils.toId( artifact ) ) )
        {
            return Collections.singletonMap( checksumAlgorithmFactory.getName(), artifactTrustedChecksum );
        }
        else
        {
            return Collections.emptyMap();
        }
    }

    @Override
    public Writer getTrustedArtifactChecksumsWriter( RepositorySystemSession session )
    {
        return trustedChecksumsWriter;
    }

    // -- TrustedChecksumsSource interface END

    private ArtifactResult createArtifactResult( Artifact artifact )
    {
        ArtifactResult artifactResult = new ArtifactResult( new ArtifactRequest().setArtifact( artifact ) );
        artifactResult.setArtifact( artifact );
        return artifactResult;
    }

    // UTs below

    @Test
    public void haveMatchingChecksumPass()
    {
        ArtifactResult artifactResult = createArtifactResult( artifactWithTrustedChecksum );
        assertThat( artifactResult.isResolved(), equalTo( true ) );

        subject.postProcess( session, Collections.singletonList( artifactResult ) );
        assertThat( artifactResult.isResolved(), equalTo( true ) );
    }

    @Test
    public void haveNoChecksumPass()
    {
        ArtifactResult artifactResult = createArtifactResult( artifactWithoutTrustedChecksum );
        assertThat( artifactResult.isResolved(), equalTo( true ) );

        subject.postProcess( session, Collections.singletonList( artifactResult ) );
        assertThat( artifactResult.isResolved(), equalTo( true ) );
    }

    @Test
    public void haveNoChecksumFailIfMissingEnabledFail()
    {
        session.setConfigProperty( "aether.artifactResolver.postProcessor.trustedChecksums.failIfMissing",
                Boolean.TRUE.toString() );
        ArtifactResult artifactResult = createArtifactResult( artifactWithoutTrustedChecksum );
        assertThat( artifactResult.isResolved(), equalTo( true ) );

        subject.postProcess( session, Collections.singletonList( artifactResult ) );
        assertThat( artifactResult.isResolved(), equalTo( false ) );
        assertThat( artifactResult.getExceptions(), not( empty() ) );
        assertThat( artifactResult.getExceptions().get( 0 ).getMessage(),
                containsString( "Missing from " + TRUSTED_SOURCE_NAME + " trusted" ) );
    }

    @Test
    public void haveMismatchingChecksumFail()
    {
        artifactTrustedChecksum = "foobar";
        ArtifactResult artifactResult = createArtifactResult( artifactWithTrustedChecksum );
        assertThat( artifactResult.isResolved(), equalTo( true ) );

        subject.postProcess( session, Collections.singletonList( artifactResult ) );
        assertThat( artifactResult.isResolved(), equalTo( false ) );
        assertThat( artifactResult.getExceptions(), not( empty() ) );
        assertThat( artifactResult.getExceptions().get( 0 ).getMessage(),
                containsString( "trusted checksum mismatch" ) );
        assertThat( artifactResult.getExceptions().get( 0 ).getMessage(),
                containsString( TRUSTED_SOURCE_NAME + "=" + artifactTrustedChecksum ) );
    }

    @Test
    public void recordCalculatedChecksum()
    {
        AtomicReference<String> recordedChecksum = new AtomicReference<>(null);
        this.trustedChecksumsWriter = new Writer()
        {
            @Override
            public void addTrustedArtifactChecksums( Artifact artifact, ArtifactRepository artifactRepository,
                                                     List<ChecksumAlgorithmFactory> checksumAlgorithmFactories,
                                                     Map<String, String> trustedArtifactChecksums )
            {
                recordedChecksum.set( trustedArtifactChecksums.get( checksumAlgorithmFactory.getName() ) );
            }
        };
        session.setConfigProperty( "aether.artifactResolver.postProcessor.trustedChecksums.record",
                Boolean.TRUE.toString() );
        ArtifactResult artifactResult = createArtifactResult( artifactWithTrustedChecksum );
        assertThat( artifactResult.isResolved(), equalTo( true ) );

        subject.postProcess( session, Collections.singletonList( artifactResult ) );
        assertThat( artifactResult.isResolved(), equalTo( true ) );

        String checksum = recordedChecksum.get();
        assertThat( checksum, notNullValue() );
        assertThat( checksum, equalTo( artifactTrustedChecksum ) );
    }
}
