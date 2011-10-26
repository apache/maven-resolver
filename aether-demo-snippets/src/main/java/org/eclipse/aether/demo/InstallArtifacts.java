/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.demo;

import java.io.File;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.demo.util.Booter;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.util.artifact.DefaultArtifact;
import org.eclipse.aether.util.artifact.SubArtifact;


/**
 * Installs a JAR and its POM to the local repository.
 */
public class InstallArtifacts
{

    public static void main( String[] args )
        throws Exception
    {
        System.out.println( "------------------------------------------------------------" );
        System.out.println( InstallArtifacts.class.getSimpleName() );

        RepositorySystem system = Booter.newRepositorySystem();

        RepositorySystemSession session = Booter.newRepositorySystemSession( system );

        Artifact jarArtifact = new DefaultArtifact( "test", "org.eclipse.aether.demo", "", "jar", "0.1-SNAPSHOT" );
        jarArtifact = jarArtifact.setFile( new File( "org.eclipse.aether.demo.jar" ) );

        Artifact pomArtifact = new SubArtifact( jarArtifact, "", "pom" );
        pomArtifact = pomArtifact.setFile( new File( "pom.xml" ) );

        InstallRequest installRequest = new InstallRequest();
        installRequest.addArtifact( jarArtifact ).addArtifact( pomArtifact );

        system.install( session, installRequest );
    }

}
