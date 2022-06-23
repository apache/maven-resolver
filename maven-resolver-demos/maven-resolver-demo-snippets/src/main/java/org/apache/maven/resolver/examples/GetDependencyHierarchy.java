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
import org.apache.maven.resolver.DefaultRepositorySystemSession;
import org.apache.maven.resolver.RepositorySystem;
import org.apache.maven.resolver.artifact.Artifact;
import org.apache.maven.resolver.artifact.DefaultArtifact;
import org.apache.maven.resolver.collection.CollectRequest;
import org.apache.maven.resolver.collection.CollectResult;
import org.apache.maven.resolver.resolution.ArtifactDescriptorRequest;
import org.apache.maven.resolver.resolution.ArtifactDescriptorResult;
import org.apache.maven.resolver.util.graph.manager.DependencyManagerUtils;
import org.apache.maven.resolver.util.graph.transformer.ConflictResolver;

/**
 * Visualizes the transitive dependencies of an artifact similar to m2e's dependency hierarchy view.
 */
public class GetDependencyHierarchy
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
        System.out.println( GetDependencyHierarchy.class.getSimpleName() );

        RepositorySystem system = Booter.newRepositorySystem( Booter.selectFactory( args ) );

        DefaultRepositorySystemSession session = Booter.newRepositorySystemSession( system );

        session.setConfigProperty( ConflictResolver.CONFIG_PROP_VERBOSE, true );
        session.setConfigProperty( DependencyManagerUtils.CONFIG_PROP_VERBOSE, true );

        Artifact artifact = new DefaultArtifact( "org.apache.maven:maven-resolver-provider:3.6.1" );

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
