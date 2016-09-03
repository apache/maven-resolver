package org.eclipse.aether.internal.test.util;

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

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.internal.test.util.IniArtifactDescriptorReader;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class IniArtifactDescriptorReaderTest
{

    private IniArtifactDescriptorReader reader;

    private RepositorySystemSession session;

    @Before
    public void setup()
        throws IOException
    {
        reader = new IniArtifactDescriptorReader( "org/eclipse/aether/internal/test/util/" );
        session = TestUtils.newSession();
    }

    @Test( expected = ArtifactDescriptorException.class )
    public void testMissingDescriptor()
        throws ArtifactDescriptorException
    {
        Artifact art = new DefaultArtifact( "missing:aid:ver:ext" );
        ArtifactDescriptorRequest request = new ArtifactDescriptorRequest( art, null, "" );
        reader.readArtifactDescriptor( session, request );
    }

    @Test
    public void testLookup()
        throws ArtifactDescriptorException
    {
        Artifact art = new DefaultArtifact( "gid:aid:ext:ver" );
        ArtifactDescriptorRequest request = new ArtifactDescriptorRequest( art, null, "" );
        ArtifactDescriptorResult description = reader.readArtifactDescriptor( session, request );

        assertEquals( request, description.getRequest() );
        assertEquals( art.setVersion( "1" ), description.getArtifact() );

        assertEquals( 1, description.getRelocations().size() );
        Artifact artifact = description.getRelocations().get( 0 );
        assertEquals( "gid", artifact.getGroupId() );
        assertEquals( "aid", artifact.getArtifactId() );
        assertEquals( "ver", artifact.getVersion() );
        assertEquals( "ext", artifact.getExtension() );

        assertEquals( 1, description.getRepositories().size() );
        RemoteRepository repo = description.getRepositories().get( 0 );
        assertEquals( "id", repo.getId() );
        assertEquals( "type", repo.getContentType() );
        assertEquals( "protocol://some/url?for=testing", repo.getUrl() );

        assertDependencies( description.getDependencies() );
        assertDependencies( description.getManagedDependencies() );

    }

    private void assertDependencies( List<Dependency> deps )
    {
        assertEquals( 4, deps.size() );

        Dependency dep = deps.get( 0 );
        assertEquals( "scope", dep.getScope() );
        assertEquals( false, dep.isOptional() );
        assertEquals( 2, dep.getExclusions().size() );
        Iterator<Exclusion> it = dep.getExclusions().iterator();
        Exclusion excl = it.next();
        assertEquals( "gid3", excl.getGroupId() );
        assertEquals( "aid", excl.getArtifactId() );
        excl = it.next();
        assertEquals( "gid2", excl.getGroupId() );
        assertEquals( "aid2", excl.getArtifactId() );

        Artifact art = dep.getArtifact();
        assertEquals( "gid", art.getGroupId() );
        assertEquals( "aid", art.getArtifactId() );
        assertEquals( "ver", art.getVersion() );
        assertEquals( "ext", art.getExtension() );

        dep = deps.get( 1 );
        assertEquals( "scope", dep.getScope() );
        assertEquals( true, dep.isOptional() );
        assertEquals( 0, dep.getExclusions().size() );

        art = dep.getArtifact();
        assertEquals( "gid", art.getGroupId() );
        assertEquals( "aid2", art.getArtifactId() );
        assertEquals( "ver", art.getVersion() );
        assertEquals( "ext", art.getExtension() );

        dep = deps.get( 2 );
        assertEquals( "scope", dep.getScope() );
        assertEquals( true, dep.isOptional() );
        assertEquals( 0, dep.getExclusions().size() );

        art = dep.getArtifact();
        assertEquals( "gid", art.getGroupId() );
        assertEquals( "aid", art.getArtifactId() );
        assertEquals( "ver3", art.getVersion() );
        assertEquals( "ext", art.getExtension() );

        dep = deps.get( 3 );
        assertEquals( "scope5", dep.getScope() );
        assertEquals( true, dep.isOptional() );
        assertEquals( 0, dep.getExclusions().size() );

        art = dep.getArtifact();
        assertEquals( "gid1", art.getGroupId() );
        assertEquals( "aid", art.getArtifactId() );
        assertEquals( "ver", art.getVersion() );
        assertEquals( "ext", art.getExtension() );
    }

}
