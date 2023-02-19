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

import java.io.File;
import java.util.List;

import org.apache.maven.resolver.examples.util.Booter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 */
@SuppressWarnings("unused")
public class ResolverDemo {
    public static void main(String[] args) throws Exception {
        System.out.println("------------------------------------------------------------");
        System.out.println(ResolverDemo.class.getSimpleName());

        Resolver resolver =
                new Resolver(Booter.selectFactory(args), "https://repo.maven.apache.org/maven2/", "target/aether-repo");
        ResolverResult result = resolver.resolve("junit", "junit", "4.13.2");

        System.out.println("Result:");
        System.out.println("classpath=" + result.getResolvedClassPath());
        System.out.println("files=" + result.getResolvedFiles());
        System.out.println("root=" + result.getRoot());
    }

    public void resolve(final String factory) throws DependencyResolutionException {
        Resolver resolver =
                new Resolver(factory, "http://localhost:8081/nexus/content/groups/public", "target/aether-repo");

        ResolverResult result = resolver.resolve("com.mycompany.app", "super-app", "1.0");

        // Get the root of the resolved tree of artifacts
        //
        DependencyNode root = result.getRoot();

        // Get the list of files for the artifacts resolved
        //
        List<File> artifacts = result.getResolvedFiles();

        // Get the classpath of the artifacts resolved
        //
        String classpath = result.getResolvedClassPath();
    }

    public void installAndDeploy(final String factory) throws InstallationException, DeploymentException {
        Resolver resolver =
                new Resolver(factory, "http://localhost:8081/nexus/content/groups/public", "target/aether-repo");

        Artifact artifact = new DefaultArtifact("com.mycompany.super", "super-core", "jar", "0.1-SNAPSHOT");
        artifact = artifact.setFile(new File("jar-from-whatever-process.jar"));
        Artifact pom = new SubArtifact(artifact, null, "pom");
        pom = pom.setFile(new File("pom-from-whatever-process.xml"));

        // Install into the local repository specified
        //
        resolver.install(artifact, pom);

        // Deploy to a remote reposistory
        //
        resolver.deploy(artifact, pom, "http://localhost:8081/nexus/content/repositories/snapshots/");
    }
}
