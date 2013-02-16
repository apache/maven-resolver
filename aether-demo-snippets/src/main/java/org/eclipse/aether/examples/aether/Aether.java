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
package org.eclipse.aether.examples.aether;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.examples.util.Booter;
import org.eclipse.aether.examples.util.ConsoleDependencyGraphDumper;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

public class Aether
{
    private String remoteRepository;

    private RepositorySystem repositorySystem;

    private LocalRepository localRepository;

    public Aether( String remoteRepository, String localRepository )
    {
        this.remoteRepository = remoteRepository;
        this.repositorySystem = Booter.newRepositorySystem();
        this.localRepository = new LocalRepository( localRepository );
    }

    private RepositorySystemSession newSession()
    {
        DefaultRepositorySystemSession session = Booter.newRepositorySystemSession( repositorySystem );
        session.setLocalRepositoryManager( repositorySystem.newLocalRepositoryManager( session, localRepository ) );
        return session;
    }

    public AetherResult resolve( String groupId, String artifactId, String version )
        throws DependencyResolutionException
    {
        RepositorySystemSession session = newSession();
        Dependency dependency =
            new Dependency( new DefaultArtifact( groupId, artifactId, "", "jar", version ), "runtime" );
        RemoteRepository central = new RemoteRepository.Builder( "central", "default", remoteRepository ).build();

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot( dependency );
        collectRequest.addRepository( central );

        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setCollectRequest( collectRequest );

        DependencyNode rootNode = repositorySystem.resolveDependencies( session, dependencyRequest ).getRoot();

        StringBuilder dump = new StringBuilder();
        displayTree( rootNode, dump );

        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        rootNode.accept( nlg );

        return new AetherResult( rootNode, nlg.getFiles(), nlg.getClassPath() );
    }

    public void install( Artifact artifact, Artifact pom )
        throws InstallationException
    {
        RepositorySystemSession session = newSession();

        InstallRequest installRequest = new InstallRequest();
        installRequest.addArtifact( artifact ).addArtifact( pom );

        repositorySystem.install( session, installRequest );
    }

    public void deploy( Artifact artifact, Artifact pom, String remoteRepository )
        throws DeploymentException
    {
        RepositorySystemSession session = newSession();

        Authentication auth = new AuthenticationBuilder().addUsername( "admin" ).addPassword( "admin123" ).build();
        RemoteRepository nexus =
            new RemoteRepository.Builder( "nexus", "default", remoteRepository ).setAuthentication( auth ).build();

        DeployRequest deployRequest = new DeployRequest();
        deployRequest.addArtifact( artifact ).addArtifact( pom );
        deployRequest.setRepository( nexus );

        repositorySystem.deploy( session, deployRequest );
    }

    private void displayTree( DependencyNode node, StringBuilder sb )
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream( 1024 );
        node.accept( new ConsoleDependencyGraphDumper( new PrintStream( os ) ) );
        sb.append( os.toString() );
    }

}
