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
package org.apache.maven.resolver.examples.resolver;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.maven.resolver.examples.util.Booter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.graph.visitor.DependencyGraphDumper;
import org.eclipse.aether.util.graph.visitor.NodeListGenerator;
import org.eclipse.aether.util.graph.visitor.PreorderDependencyNodeConsumerVisitor;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

/**
 */
public class Resolver {
    private final String[] args;

    private final String remoteRepository;

    private final RepositorySystem repositorySystem;

    private final LocalRepository localRepository;

    public Resolver(String[] args, String remoteRepository, Path localRepository) {
        this.args = args;
        this.remoteRepository = remoteRepository;
        this.repositorySystem = Booter.newRepositorySystem(Booter.selectFactory(args));
        this.localRepository = new LocalRepository(localRepository);
    }

    private RepositorySystemSession newSession() {
        return Booter.newRepositorySystemSession(repositorySystem, Booter.selectFs(args))
                .withLocalRepositories(localRepository)
                .setRepositoryListener(null)
                .setTransferListener(null)
                .build();
    }

    public ResolverResult resolve(String groupId, String artifactId, String version)
            throws DependencyResolutionException {
        RepositorySystemSession session = newSession();
        Dependency dependency = new Dependency(new DefaultArtifact(groupId, artifactId, "", "jar", version), "runtime");
        RemoteRepository central = new RemoteRepository.Builder("central", "default", remoteRepository).build();

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(dependency);
        collectRequest.addRepository(central);

        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setCollectRequest(collectRequest);

        DependencyNode rootNode =
                repositorySystem.resolveDependencies(session, dependencyRequest).getRoot();

        StringBuilder dump = new StringBuilder();
        displayTree(rootNode, dump);
        System.out.println("Tree:");
        System.out.println(dump);

        NodeListGenerator nlg = new NodeListGenerator();
        rootNode.accept(new PreorderDependencyNodeConsumerVisitor(nlg));

        return new ResolverResult(rootNode, nlg.getFiles(), nlg.getClassPath());
    }

    public void install(Artifact artifact, Artifact pom) throws InstallationException {
        RepositorySystemSession session = newSession();

        InstallRequest installRequest = new InstallRequest();
        installRequest.addArtifact(artifact).addArtifact(pom);

        repositorySystem.install(session, installRequest);
    }

    public void deploy(Artifact artifact, Artifact pom, String remoteRepository) throws DeploymentException {
        RepositorySystemSession session = newSession();

        Authentication auth = new AuthenticationBuilder()
                .addUsername("admin")
                .addPassword("admin123")
                .build();
        RemoteRepository nexus = new RemoteRepository.Builder("nexus", "default", remoteRepository)
                .setAuthentication(auth)
                .build();

        DeployRequest deployRequest = new DeployRequest();
        deployRequest.addArtifact(artifact).addArtifact(pom);
        deployRequest.setRepository(nexus);

        repositorySystem.deploy(session, deployRequest);
    }

    private void displayTree(DependencyNode node, StringBuilder sb) {
        ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
        PrintStream ps = new PrintStream(os, true, StandardCharsets.UTF_8);
        node.accept(new DependencyGraphDumper(ps::println));
        sb.append(os);
    }
}
