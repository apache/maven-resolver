/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.examples;

import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.examples.util.Booter;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

/**
 * Determines all available versions of an artifact.
 */
public class FindAvailableVersions
{

    public static void main( String[] args )
        throws Exception
    {
        System.out.println( "------------------------------------------------------------" );
        System.out.println( FindAvailableVersions.class.getSimpleName() );

        RepositorySystem system = Booter.newRepositorySystem();

        RepositorySystemSession session = Booter.newRepositorySystemSession( system );

        Artifact artifact = new DefaultArtifact( "org.eclipse.aether:aether-util:[0,)" );

        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact( artifact );
        rangeRequest.setRepositories( Booter.newRepositories( system, session ) );

        VersionRangeResult rangeResult = system.resolveVersionRange( session, rangeRequest );

        List<Version> versions = rangeResult.getVersions();

        System.out.println( "Available versions " + versions );
    }

}
