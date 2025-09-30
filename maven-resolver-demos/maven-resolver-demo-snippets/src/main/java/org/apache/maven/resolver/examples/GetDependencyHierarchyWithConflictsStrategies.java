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

import java.util.List;

import org.apache.maven.resolver.examples.util.Booter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.RepositorySystemSession.SessionBuilder;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.impl.collect.bf.BfDependencyCollector;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.ConfigurableVersionSelector;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaDependencyContextRefiner;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.JavaScopeSelector;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;
import org.eclipse.aether.util.graph.visitor.DependencyGraphDumper;

/**
 * Demo of "nearest" vs "highest" winner selection.
 */
public class GetDependencyHierarchyWithConflictsStrategies {

    /**
     * Main.
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        System.out.println("------------------------------------------------------------");
        System.out.println(GetDependencyHierarchyWithConflictsStrategies.class.getSimpleName());

        CollectRequest collectRequest;

        // okttp cleanly shows difference between nearest/closest
        collectRequest = new CollectRequest();
        collectRequest.setRootArtifact(new DefaultArtifact("demo:demo:1.0"));
        collectRequest.setDependencies(
                List.of(new Dependency(new DefaultArtifact("com.squareup.okhttp3:okhttp:jar:4.12.0"), "compile")));
        runItWithStrategy(args, ConfigurableVersionSelector.NEAREST_SELECTION_STRATEGY, collectRequest);
        runItWithStrategy(args, ConfigurableVersionSelector.HIGHEST_SELECTION_STRATEGY, collectRequest);

        // MENFORCER-408 inspired
        collectRequest = new CollectRequest();
        collectRequest.setRootArtifact(new DefaultArtifact("demo:demo:1.0"));
        collectRequest.setDependencies(List.of(
                new Dependency(new DefaultArtifact("org.seleniumhq.selenium:selenium-java:jar:3.0.1"), "test")));
        collectRequest.setManagedDependencies(List.of(
                new Dependency(new DefaultArtifact("org.seleniumhq.selenium:selenium-java:jar:3.0.1"), "test"),
                new Dependency(new DefaultArtifact("org.seleniumhq.selenium:selenium-remote-driver:jar:3.0.1"), "test"),
                new Dependency(new DefaultArtifact("com.codeborne:phantomjsdriver:jar:1.3.0"), "test")));
        runItWithStrategy(args, ConfigurableVersionSelector.NEAREST_SELECTION_STRATEGY, collectRequest);
        runItWithStrategy(args, ConfigurableVersionSelector.HIGHEST_SELECTION_STRATEGY, collectRequest);
    }

    private static void runItWithStrategy(String[] args, String selectionStrategy, CollectRequest collectRequest)
            throws Exception {
        System.out.println();
        System.out.println(selectionStrategy);
        try (RepositorySystem system = Booter.newRepositorySystem(Booter.selectFactory(args))) {
            SessionBuilder sessionBuilder = Booter.newRepositorySystemSession(system, Booter.selectFs(args));
            sessionBuilder.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, ConflictResolver.Verbosity.STANDARD);
            sessionBuilder.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);
            sessionBuilder.setConfigProperty(
                    ConfigurableVersionSelector.CONFIG_PROP_SELECTION_STRATEGY, selectionStrategy);
            sessionBuilder.setConfigProperty(
                    BfDependencyCollector.CONFIG_PROP_SKIPPER, BfDependencyCollector.VERSIONED_SKIPPER);
            try (CloseableSession session = sessionBuilder
                    .setDependencyGraphTransformer(new ChainedDependencyGraphTransformer(
                            new ConflictResolver(
                                    new ConfigurableVersionSelector(),
                                    new JavaScopeSelector(),
                                    new SimpleOptionalitySelector(),
                                    new JavaScopeDeriver()),
                            new JavaDependencyContextRefiner()))
                    .setRepositoryListener(null)
                    .setTransferListener(null)
                    .build()) {

                collectRequest.setRepositories(Booter.newRepositories(system, session));

                CollectResult result = system.collectDependencies(session, collectRequest);
                System.out.println("tree:");
                result.getRoot().accept(new DependencyGraphDumper(System.out::println));

                List<DependencyNode> selected =
                        system.flattenDependencyNodes(session, result.getRoot(), (node, parents) -> !node.getData()
                                .containsKey(ConflictResolver.NODE_DATA_WINNER));
                System.out.println("cp:");
                selected.forEach(System.out::println);
            }
        }
    }
}
