/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.resolver.examples;

import java.io.File;
import java.util.Collections;

import org.apache.maven.resolver.examples.util.Booter;
import org.apache.maven.resolver.examples.util.ConsoleDependencyGraphDumper;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

/**
 * Visualizes the transitive dependencies of an artifact similar to m2e's dependency hierarchy view. Artifact in this
 * test is not "plain" one as is original "demo" {@link GetDependencyHierarchy}, but specially crafted for case
 * described in MRESOLVER-345.
 *
 * @see <a href="https://issues.apache.org/jira/browse/MRESOLVER-345">MRESOLVER-345</a>
 */
public class DependencyHierarchyWithRanges {

    /**
     * Main.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("------------------------------------------------------------");
        System.out.println(DependencyHierarchyWithRanges.class.getSimpleName());

        RepositorySystem system = Booter.newRepositorySystem(Booter.selectFactory(args));

        DefaultRepositorySystemSession session = Booter.newRepositorySystemSession(system);

        session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_IGNORE); // to not bother with checksums
        session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
        session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);

        // this artifact is in "remote" repository in src/main/resources/remote-repository
        Artifact artifact = new DefaultArtifact("org.apache.maven.resolver.demo.mresolver345:a:1.0");

        File remoteRepoBasedir = new File("src/main/remote-repository");

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(artifact);
        descriptorRequest.setRepositories(Collections.singletonList(new RemoteRepository.Builder(
                        "remote", "default", remoteRepoBasedir.toURI().toASCIIString())
                .build()));
        ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRootArtifact(descriptorResult.getArtifact());
        collectRequest.setDependencies(descriptorResult.getDependencies());
        collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());
        collectRequest.setRepositories(descriptorRequest.getRepositories());

        CollectResult collectResult = system.collectDependencies(session, collectRequest);

        collectResult.getRoot().accept(new ConsoleDependencyGraphDumper());
    }
}
