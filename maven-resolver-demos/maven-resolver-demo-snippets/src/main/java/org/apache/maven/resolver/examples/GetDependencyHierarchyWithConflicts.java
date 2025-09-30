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

import org.apache.maven.resolver.examples.util.Booter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.RepositorySystemSession.SessionBuilder;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.UnsolvableVersionConflictException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.ClassicConflictResolver;
import org.eclipse.aether.util.graph.transformer.ConfigurableVersionSelector;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaDependencyContextRefiner;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.JavaScopeSelector;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;

/**
 * Visualizes the transitive dependencies of an artifact similar to m2e's dependency hierarchy view.
 */
public class GetDependencyHierarchyWithConflicts {

    /**
     * Main.
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        System.out.println("------------------------------------------------------------");
        System.out.println(GetDependencyHierarchyWithConflicts.class.getSimpleName());

        // incompatible versions: two incompatible versions present in graph
        try (RepositorySystem system = Booter.newRepositorySystem(Booter.selectFactory(args))) {
            SessionBuilder sessionBuilder = Booter.newRepositorySystemSession(system, Booter.selectFs(args));
            sessionBuilder.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
            sessionBuilder.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);
            try (CloseableSession session = sessionBuilder
                    .setDependencyGraphTransformer(new ChainedDependencyGraphTransformer(
                            new ClassicConflictResolver(
                                    new ConfigurableVersionSelector(
                                            new ConfigurableVersionSelector.MajorVersionConvergence(
                                                    new ConfigurableVersionSelector.Nearest())),
                                    new JavaScopeSelector(),
                                    new SimpleOptionalitySelector(),
                                    new JavaScopeDeriver()),
                            new JavaDependencyContextRefiner()))
                    .build()) {
                Artifact artifact = new DefaultArtifact("org.apache.maven.shared:maven-dependency-tree:3.0.1");

                ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
                descriptorRequest.setArtifact(artifact);
                descriptorRequest.setRepositories(Booter.newRepositories(system, session));
                ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

                CollectRequest collectRequest = new CollectRequest();
                collectRequest.setRootArtifact(descriptorResult.getArtifact());
                collectRequest.setDependencies(descriptorResult.getDependencies());
                collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());
                collectRequest.setRepositories(descriptorRequest.getRepositories());

                system.collectDependencies(session, collectRequest);
                throw new IllegalStateException("should fail");
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e.getCause() instanceof UnsolvableVersionConflictException) {
                String cause = e.getCause().getMessage();
                if (!cause.contains(
                        "Incompatible versions for org.apache.maven:maven-core, incompatible versions: [2.0], all versions [2.0, 3.0.4]")) {
                    throw new IllegalStateException("should fail due incompatible versions");
                }
            } else {
                throw new IllegalStateException("should fail due incompatible versions");
            }
        }

        // dependency divergence: multiple versions of same GA present in graph
        try (RepositorySystem system = Booter.newRepositorySystem(Booter.selectFactory(args))) {
            SessionBuilder sessionBuilder = Booter.newRepositorySystemSession(system, Booter.selectFs(args));
            sessionBuilder.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
            sessionBuilder.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);
            try (CloseableSession session = sessionBuilder
                    .setDependencyGraphTransformer(new ChainedDependencyGraphTransformer(
                            new ClassicConflictResolver(
                                    new ConfigurableVersionSelector(new ConfigurableVersionSelector.VersionConvergence(
                                            new ConfigurableVersionSelector.Nearest())),
                                    new JavaScopeSelector(),
                                    new SimpleOptionalitySelector(),
                                    new JavaScopeDeriver()),
                            new JavaDependencyContextRefiner()))
                    .build()) {
                Artifact artifact = new DefaultArtifact("org.apache.maven.shared:maven-dependency-tree:3.1.0");

                ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
                descriptorRequest.setArtifact(artifact);
                descriptorRequest.setRepositories(Booter.newRepositories(system, session));
                ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

                CollectRequest collectRequest = new CollectRequest();
                collectRequest.setRootArtifact(descriptorResult.getArtifact());
                collectRequest.setDependencies(descriptorResult.getDependencies());
                collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());
                collectRequest.setRepositories(descriptorRequest.getRepositories());

                system.collectDependencies(session, collectRequest);
                throw new IllegalStateException("should fail");
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e.getCause() instanceof UnsolvableVersionConflictException) {
                String cause = e.getCause().getMessage();
                if (!cause.contains(
                        "Convergence violated for org.codehaus.plexus:plexus-utils, versions present: [2.1, 1.5.5, 2.0.6]")) {
                    throw new IllegalStateException("should fail due convergence violation");
                }
            } else {
                throw new IllegalStateException("should fail due convergence violation");
            }
        }
    }
}
