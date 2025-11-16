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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.maven.resolver.examples.util.Booter;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * Resolves the transitive (compile) dependencies of an artifact.
 */
public class ResolveTransitiveDependenciesParallel {

    /**
     * Main.
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        System.out.println("------------------------------------------------------------");
        System.out.println(ResolveTransitiveDependencies.class.getSimpleName());

        System.setProperty("aether.syncContext.named.time", "3");

        RepositorySystem system = Booter.newRepositorySystem(Booter.selectFactory(args));

        DefaultRepositorySystemSession session = Booter.newRepositorySystemSession(system);
        session.setTransferListener(null);

        Artifact bigArtifact1 = new DefaultArtifact("org.bytedeco:llvm:jar:linux-arm64:16.0.4-1.5.9");
        Artifact bigArtifact2 = new DefaultArtifact("org.bytedeco:llvm:jar:linux-armhf:16.0.4-1.5.9");
        Artifact bigArtifact3 = new DefaultArtifact("org.bytedeco:llvm:jar:linux-ppc64le:16.0.4-1.5.9");
        Artifact bigArtifact4 = new DefaultArtifact("org.bytedeco:llvm:jar:linux-x86:16.0.4-1.5.9");
        Artifact bigArtifact5 = new DefaultArtifact("org.bytedeco:llvm:jar:linux-x86_64:16.0.4-1.5.9");
        Artifact bigArtifact6 = new DefaultArtifact("org.bytedeco:llvm:jar:macosx-arm64:16.0.4-1.5.9");
        Artifact bigArtifact7 = new DefaultArtifact("org.bytedeco:llvm:jar:macosx-x86_64:16.0.4-1.5.9");
        Artifact bigArtifact8 = new DefaultArtifact("org.bytedeco:llvm:jar:windows-x86:16.0.4-1.5.9");
        Artifact bigArtifact9 = new DefaultArtifact("org.bytedeco:llvm:jar:windows-x86_64:16.0.4-1.5.9");

        CountDownLatch latch = new CountDownLatch(8);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);

        Thread thread1 = new Thread(resolveWithDependencies(
                latch,
                success,
                fail,
                system,
                session,
                Booter.newRepositories(system, session),
                "org.example:test:1",
                bigArtifact1,
                bigArtifact2));
        Thread thread2 = new Thread(resolveWithDependencies(
                latch,
                success,
                fail,
                system,
                session,
                Booter.newRepositories(system, session),
                "org.example:test:2",
                bigArtifact2,
                bigArtifact3));
        Thread thread3 = new Thread(resolveWithDependencies(
                latch,
                success,
                fail,
                system,
                session,
                Booter.newRepositories(system, session),
                "org.example:test:3",
                bigArtifact3,
                bigArtifact4));
        Thread thread4 = new Thread(resolveWithDependencies(
                latch,
                success,
                fail,
                system,
                session,
                Booter.newRepositories(system, session),
                "org.example:test:4",
                bigArtifact4,
                bigArtifact5));
        Thread thread5 = new Thread(resolveWithDependencies(
                latch,
                success,
                fail,
                system,
                session,
                Booter.newRepositories(system, session),
                "org.example:test:5",
                bigArtifact5,
                bigArtifact6));
        Thread thread6 = new Thread(resolveWithDependencies(
                latch,
                success,
                fail,
                system,
                session,
                Booter.newRepositories(system, session),
                "org.example:test:6",
                bigArtifact6,
                bigArtifact7));
        Thread thread7 = new Thread(resolveWithDependencies(
                latch,
                success,
                fail,
                system,
                session,
                Booter.newRepositories(system, session),
                "org.example:test:7",
                bigArtifact7,
                bigArtifact8));
        Thread thread8 = new Thread(resolveWithDependencies(
                latch,
                success,
                fail,
                system,
                session,
                Booter.newRepositories(system, session),
                "org.example:test:8",
                bigArtifact8,
                bigArtifact9));

        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();
        thread5.start();
        thread6.start();
        thread7.start();
        thread8.start();

        latch.await();

        System.out.println("TOTAL success=" + success.get() + "; fail=" + fail.get());
    }

    private static Runnable resolveWithDependencies(
            CountDownLatch latch,
            AtomicInteger success,
            AtomicInteger fail,
            RepositorySystem system,
            RepositorySystemSession session,
            List<RemoteRepository> repositories,
            String gav,
            Artifact... deps) {
        return () -> {
            try {
                CollectRequest collectRequest = new CollectRequest();
                collectRequest.setRootArtifact(new DefaultArtifact(gav));
                for (Artifact dep : deps) {
                    collectRequest.addDependency(new Dependency(dep, JavaScopes.COMPILE));
                }
                collectRequest.setRepositories(repositories);
                DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
                List<ArtifactResult> artifactResults =
                        system.resolveDependencies(session, dependencyRequest).getArtifactResults();
                int resolved = 9;
                int fails = 0;
                for (ArtifactResult artifactResult : artifactResults) {
                    if (artifactResult.isResolved()) {
                        resolved++;
                    } else {
                        fails++;
                    }
                }
                System.out.println("DONE " + gav + ": resolved " + resolved + "; failed " + fails);
                success.getAndIncrement();
            } catch (Exception e) {
                System.out.println("FAILED " + gav + ": " + e.getMessage());
                fail.getAndIncrement();
            } finally {
                latch.countDown();
            }
        };
    }
}
