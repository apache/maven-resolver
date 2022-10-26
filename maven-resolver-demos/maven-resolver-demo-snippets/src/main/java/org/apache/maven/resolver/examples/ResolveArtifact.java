package org.apache.maven.resolver.examples;

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

import org.apache.maven.resolver.examples.util.Booter;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Resolves a single artifact.
 */
public class ResolveArtifact
{

    /**
     * Main.
     * @param args
     * @throws Exception
     */
    public static void main( String[] args )
        throws Exception
    {
        System.out.println( "------------------------------------------------------------" );
        System.out.println( ResolveArtifact.class.getSimpleName() );

        try ( Booter booter = new Booter( args ) )
        {
            RepositorySystem system = booter.getRepositorySystem();

            DefaultRepositorySystemSession session = booter.getSession();

            Artifact artifact;
            ArtifactRequest artifactRequest;
            ArtifactResult artifactResult;

            // artifact
            artifact = new DefaultArtifact( "org.apache.maven.resolver:maven-resolver-util:1.3.3" );

            artifactRequest = new ArtifactRequest();
            artifactRequest.setArtifact( artifact );
            artifactRequest.setRepositories( Booter.newRepositories( system, session ) );

            artifactResult = system.resolveArtifact( session, artifactRequest );

            artifact = artifactResult.getArtifact();

            System.out.println( artifact + " resolved to  " + artifact.getFile() );

            // signature
            session.setChecksumPolicy( RepositoryPolicy.CHECKSUM_POLICY_FAIL );

            artifact = new DefaultArtifact( "org.apache.maven.resolver:maven-resolver-util:jar.asc:1.3.3" );

            artifactRequest = new ArtifactRequest();
            artifactRequest.setArtifact( artifact );
            artifactRequest.setRepositories( Booter.newRepositories( system, session ) );

            artifactResult = system.resolveArtifact( session, artifactRequest );

            artifact = artifactResult.getArtifact();

            System.out.println( artifact + " resolved signature to  " + artifact.getFile() );
        }
    }
}
