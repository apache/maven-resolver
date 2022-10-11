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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * UT for {@link GroupIdRemoteRepositoryFilterSource}.
 */
public class GroupIdRemoteRepositoryFilterSourceTest extends RemoteRepositoryFilterSourceTestSupport
{
    @Override
    protected GroupIdRemoteRepositoryFilterSource getRemoteRepositoryFilterSource(
            DefaultRepositorySystemSession session, RemoteRepository remoteRepository )
    {
        return new GroupIdRemoteRepositoryFilterSource();
    }

    @Override
    protected void enableSource( DefaultRepositorySystemSession session )
    {
        session.setConfigProperty( "aether.remoteRepositoryFilter." + GroupIdRemoteRepositoryFilterSource.NAME,
                Boolean.TRUE.toString() );
    }

    protected void allowArtifact( DefaultRepositorySystemSession session, RemoteRepository remoteRepository,
                                  Artifact artifact )
    {
        try ( DefaultRepositorySystemSession newSession = new DefaultRepositorySystemSession( session ) )
        {
            Artifact resolvedArtifact = artifact.setFile( Files.createTempFile( "test", "tmp" ).toFile() );
            ArtifactResult artifactResult = new ArtifactResult( new ArtifactRequest( resolvedArtifact,
                    Collections.singletonList( remoteRepository ), "context" ) );
            artifactResult.setArtifact( resolvedArtifact );
            artifactResult.setRepository( remoteRepository );
            List<ArtifactResult> artifactResults = Collections.singletonList( artifactResult );
            enableSource( newSession );
            newSession.setConfigProperty( "aether.remoteRepositoryFilter." + GroupIdRemoteRepositoryFilterSource.NAME
                    + ".record", Boolean.TRUE.toString() );
            getRemoteRepositoryFilterSource( newSession, remoteRepository )
                    .postProcess( newSession, artifactResults );
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }

    }
}
