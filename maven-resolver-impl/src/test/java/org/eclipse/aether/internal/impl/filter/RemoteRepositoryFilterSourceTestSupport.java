package org.eclipse.aether.internal.impl.filter;

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

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilterSource;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * UT helper for {@link RemoteRepositoryFilterSource} UTs.
 */
public abstract class RemoteRepositoryFilterSourceTestSupport
{
    private final Artifact acceptedArtifact = new DefaultArtifact( "org.one:aid:1.0" );

    private final Artifact notAcceptedArtifact = new DefaultArtifact( "org.two:aid:1.0" );

    private DefaultRepositorySystemSession session;

    private RemoteRepository remoteRepository;

    private RemoteRepositoryFilterSource subject;

    @Before
    public void setup()
    {
        remoteRepository = new RemoteRepository.Builder( "test", "default", "https://irrelevant.com" ).build();
        session = TestUtils.newSession();
        subject = getRemoteRepositoryFilterSource( session, remoteRepository );
    }

    protected abstract RemoteRepositoryFilterSource getRemoteRepositoryFilterSource(
            DefaultRepositorySystemSession session, RemoteRepository remoteRepository );

    protected abstract void enableSource( DefaultRepositorySystemSession session );

    protected abstract void allowArtifact(
            DefaultRepositorySystemSession session, RemoteRepository remoteRepository, Artifact artifact );

    @Test
    public void notEnabled()
    {
        RemoteRepositoryFilter filter = subject.getRemoteRepositoryFilter( session );
        assertThat( filter, nullValue() );
    }

    @Test
    public void acceptedArtifact()
    {
        allowArtifact( session, remoteRepository, acceptedArtifact );
        enableSource( session );

        RemoteRepositoryFilter filter = subject.getRemoteRepositoryFilter( session );
        assertThat( filter, notNullValue() );

        RemoteRepositoryFilter.Result result = filter.acceptArtifact( remoteRepository, acceptedArtifact );

        assertThat( result.isAccepted(), equalTo( true ) );
        assertThat( result.reasoning(), containsString( "allowed from test" ) );
    }

    @Test
    public void notAcceptedArtifact()
    {
        allowArtifact( session, remoteRepository, acceptedArtifact );
        enableSource( session );

        RemoteRepositoryFilter filter = subject.getRemoteRepositoryFilter( session );
        assertThat( filter, notNullValue() );

        RemoteRepositoryFilter.Result result = filter.acceptArtifact( remoteRepository, notAcceptedArtifact );

        assertThat( result.isAccepted(), equalTo( false ) );
        assertThat( result.reasoning(), containsString( "NOT allowed from test" ) );
    }
}
