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
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * Deploys a JAR and its POM to a remote repository.
 */
public class DeferredDeployArtifacts {

    /**
     * Main.
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        System.out.println("------------------------------------------------------------");
        System.out.println(DeferredDeployArtifacts.class.getSimpleName());

        try (RepositorySystem system = Booter.newRepositorySystem(Booter.selectFactory(args));
                CloseableSession session = Booter.newRepositorySystemSession(system)
                        .setConfigProperty(ConfigurationProperties.DEPLOY_AT_SESSION_END, Boolean.TRUE)
                        .build()) {

            RemoteRepository distRepo = new RemoteRepository.Builder(
                            "org.apache.maven.aether.examples",
                            "default",
                            new File("target/dist-repo").toURI().toString())
                    .build();

            for (int i = 1; i < 4; i++) {
                Artifact jarArtifact = new DefaultArtifact(
                        "test", "org.apache.maven.aether.examples.deferred", "", "jar", "0." + i + "-SNAPSHOT");
                jarArtifact = jarArtifact.setPath(new File("src/main/data/demo.jar").toPath());

                Artifact pomArtifact = new SubArtifact(jarArtifact, "", "pom");
                pomArtifact = pomArtifact.setPath(new File("pom.xml").toPath());

                DeployRequest deployRequest = new DeployRequest();
                deployRequest.addArtifact(jarArtifact).addArtifact(pomArtifact);
                deployRequest.setRepository(distRepo);

                system.deploy(session, deployRequest);
            }
        }
    }
}
