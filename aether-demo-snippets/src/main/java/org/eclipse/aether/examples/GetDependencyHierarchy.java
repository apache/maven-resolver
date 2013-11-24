/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.examples;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.examples.util.Booter;
import org.eclipse.aether.examples.util.ConsoleDependencyGraphDumper;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

/**
 * Visualizes the transitive dependencies of an artifact similar to m2e's dependency hierarchy view.
 */
public class GetDependencyHierarchy
{

    public static void main( String[] args )
        throws Exception
    {
        System.out.println( "------------------------------------------------------------" );
        System.out.println( GetDependencyHierarchy.class.getSimpleName() );

        RepositorySystem system = Booter.newRepositorySystem();

        DefaultRepositorySystemSession session = Booter.newRepositorySystemSession( system );

        session.setConfigProperty( ConflictResolver.CONFIG_PROP_VERBOSE, true );
        session.setConfigProperty( DependencyManagerUtils.CONFIG_PROP_VERBOSE, true );

        Artifact artifact = new DefaultArtifact( "org.apache.maven:maven-aether-provider:3.1.0" );

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact( artifact );
        descriptorRequest.setRepositories( Booter.newRepositories( system, session ) );
        ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor( session, descriptorRequest );

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRootArtifact( descriptorResult.getArtifact() );
        collectRequest.setDependencies( descriptorResult.getDependencies() );
        collectRequest.setManagedDependencies( descriptorResult.getManagedDependencies() );
        collectRequest.setRepositories( descriptorRequest.getRepositories() );

        CollectResult collectResult = system.collectDependencies( session, collectRequest );

        collectResult.getRoot().accept( new ConsoleDependencyGraphDumper() );
    }

}
