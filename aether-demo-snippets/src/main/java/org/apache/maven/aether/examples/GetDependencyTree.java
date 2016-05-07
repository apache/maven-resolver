package org.apache.maven.aether.examples;

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

import org.apache.maven.aether.examples.util.Booter;
import org.apache.maven.aether.examples.util.ConsoleDependencyGraphDumper;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;

/**
 * Collects the transitive dependencies of an artifact.
 */
public class GetDependencyTree
{

    public static void main( String[] args )
        throws Exception
    {
        System.out.println( "------------------------------------------------------------" );
        System.out.println( GetDependencyTree.class.getSimpleName() );

        RepositorySystem system = Booter.newRepositorySystem();

        RepositorySystemSession session = Booter.newRepositorySystemSession( system );

        Artifact artifact = new DefaultArtifact( "org.apache.maven:maven-aether-provider:3.1.0" );

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot( new Dependency( artifact, "" ) );
        collectRequest.setRepositories( Booter.newRepositories( system, session ) );

        CollectResult collectResult = system.collectDependencies( session, collectRequest );

        collectResult.getRoot().accept( new ConsoleDependencyGraphDumper() );
    }

}
