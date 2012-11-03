/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.examples;

import java.io.File;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.examples.util.Booter;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * Deploys a JAR and its POM to a remote repository.
 */
public class DeployArtifacts
{

    public static void main( String[] args )
        throws Exception
    {
        System.out.println( "------------------------------------------------------------" );
        System.out.println( DeployArtifacts.class.getSimpleName() );

        RepositorySystem system = Booter.newRepositorySystem();

        RepositorySystemSession session = Booter.newRepositorySystemSession( system );

        Artifact jarArtifact = new DefaultArtifact( "test", "org.eclipse.aether.examples", "", "jar", "0.1-SNAPSHOT" );
        jarArtifact = jarArtifact.setFile( new File( "src/main/data/demo.jar" ) );

        Artifact pomArtifact = new SubArtifact( jarArtifact, "", "pom" );
        pomArtifact = pomArtifact.setFile( new File( "pom.xml" ) );

        RemoteRepository distRepo =
            new RemoteRepository.Builder( "org.eclipse.aether.examples", "default",
                                  new File( "target/dist-repo" ).toURI().toString() ).build();

        DeployRequest deployRequest = new DeployRequest();
        deployRequest.addArtifact( jarArtifact ).addArtifact( pomArtifact );
        deployRequest.setRepository( distRepo );

        system.deploy( session, deployRequest );
    }

}
