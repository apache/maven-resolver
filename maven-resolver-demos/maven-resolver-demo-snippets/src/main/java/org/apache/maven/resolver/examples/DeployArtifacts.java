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

import org.apache.maven.resolver.examples.util.Booter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * Deploys a JAR and its POM to a remote repository.
 */
public class DeployArtifacts {

    /**
     * Main.
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        System.out.println("------------------------------------------------------------");
        System.out.println(DeployArtifacts.class.getSimpleName());

        RepositorySystem system = Booter.newRepositorySystem(Booter.selectFactory(args));

        RepositorySystemSession session = Booter.newRepositorySystemSession(system);

        Artifact jarArtifact =
                new DefaultArtifact("test", "org.apache.maven.aether.examples", "", "jar", "0.1-SNAPSHOT");
        jarArtifact = jarArtifact.setFile(new File("src/main/data/demo.jar"));

        Artifact pomArtifact = new SubArtifact(jarArtifact, "", "pom");
        pomArtifact = pomArtifact.setFile(new File("pom.xml"));

        RemoteRepository distRepo = new RemoteRepository.Builder(
                        "org.apache.maven.aether.examples",
                        "default",
                        new File("target/dist-repo").toURI().toString())
                .build();

        DeployRequest deployRequest = new DeployRequest();
        deployRequest.addArtifact(jarArtifact).addArtifact(pomArtifact);
        deployRequest.setRepository(distRepo);

        system.deploy(session, deployRequest);
    }
}
