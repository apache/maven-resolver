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
import org.apache.maven.resolver.examples.util.ConsoleDependencyGraphDumper;
import org.apache.maven.resolver.examples.util.ReverseTreeRepositoryListener;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.listener.ChainedRepositoryListener;

/**
 * Example of building reverse dependency tree.
 */
public class ReverseDependencyTree
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
        System.out.println( ReverseDependencyTree.class.getSimpleName() );

        RepositorySystem system = Booter.newRepositorySystem( Booter.selectFactory( args ) );

        DefaultRepositorySystemSession session = Booter.newRepositorySystemSession( system );
        session.setRepositoryListener( new ChainedRepositoryListener( session.getRepositoryListener(),
                new ReverseTreeRepositoryListener() ) );

        session.setConfigProperty( ConflictResolver.CONFIG_PROP_VERBOSE, true );
        session.setConfigProperty( DependencyManagerUtils.CONFIG_PROP_VERBOSE, true );

        Artifact artifact = new DefaultArtifact( "org.apache.maven:maven-resolver-provider:3.6.1" );

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact( artifact );
        descriptorRequest.setRepositories( Booter.newRepositories( system, session ) );
        ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor( session, descriptorRequest );

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRequestContext( "demo" );
        collectRequest.setRootArtifact( descriptorResult.getArtifact() );
        collectRequest.setDependencies( descriptorResult.getDependencies() );
        collectRequest.setManagedDependencies( descriptorResult.getManagedDependencies() );
        collectRequest.setRepositories( descriptorRequest.getRepositories() );

        system.collectDependencies( session, collectRequest );
    }

}
