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
package org.apache.maven.resolver.examples.util;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession.SessionBuilder;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.supplier.SessionBuilderSupplier;
import org.eclipse.aether.util.graph.visitor.DependencyGraphDumper;

/**
 * A helper to boot the repository system and a repository system session.
 */
public class Booter {
    public static final String FACTORY_SUPPLIER = "supplier";

    public static final String FACTORY_SISU = "sisu";

    public static final String FS_DEFAULT = "default";

    public static final String FS_JIMFS = "jimfs";

    public static final DependencyGraphDumper DUMPER_SOUT = new DependencyGraphDumper(System.out::println);

    public static String selectFactory(String[] args) {
        if (args == null || args.length == 0) {
            return FACTORY_SUPPLIER;
        } else {
            return args[0];
        }
    }

    public static String selectFs(String[] args) {
        if (args == null || args.length < 2) {
            return FS_DEFAULT;
        } else {
            return args[1];
        }
    }

    public static RepositorySystem newRepositorySystem(final String factory) {
        System.out.println("Using factory: " + factory);
        return switch (factory) {
            case FACTORY_SUPPLIER -> org.apache.maven.resolver.examples.supplier.SupplierRepositorySystemFactory
                    .newRepositorySystem();
            case FACTORY_SISU -> org.apache.maven.resolver.examples.sisu.SisuRepositorySystemFactory
                    .newRepositorySystem();
            default -> throw new IllegalArgumentException("Unknown factory: " + factory);
        };
    }

    public static SessionBuilder newRepositorySystemSession(RepositorySystem system, String fs) {
        System.out.println("Using FS: " + fs);
        boolean close;
        Path localRepository;
        if (FS_JIMFS.equals(fs)) {
            close = true;
            localRepository = Jimfs.newFileSystem(Configuration.unix()).getPath("/demo");
        } else {
            close = false;
            localRepository = Path.of("target/example-snippets-repo");
        }
        // Path localRepository = Path.of("target/example-snippets-repo");
        SessionBuilder result = new SessionBuilderSupplier(system)
                .get()
                .withLocalRepositoryBaseDirectories(localRepository)
                .setRepositoryListener(new ConsoleRepositoryListener())
                .setTransferListener(new ConsoleTransferListener())
                .setConfigProperty("aether.generator.gpg.enabled", Boolean.TRUE.toString())
                .setConfigProperty(
                        "aether.generator.gpg.keyFilePath",
                        Paths.get("src/main/resources/alice.key")
                                .toAbsolutePath()
                                .toString());

        // uncomment to generate dirty trees
        // session.setDependencyGraphTransformer( null );

        if (close) {
            result.addOnSessionEndedHandler(() -> {
                try {
                    localRepository.getFileSystem().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        return result;
    }

    public static List<RemoteRepository> newRepositories(RepositorySystem system, RepositorySystemSession session) {
        return new ArrayList<>(Collections.singletonList(newCentralRepository()));
    }

    private static RemoteRepository newCentralRepository() {
        return new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/")
                .setReleasePolicy(new RepositoryPolicy(
                        true,
                        RepositoryPolicy.UPDATE_POLICY_NEVER,
                        RepositoryPolicy.UPDATE_POLICY_DAILY,
                        RepositoryPolicy.CHECKSUM_POLICY_FAIL))
                .setSnapshotPolicy(new RepositoryPolicy(
                        false,
                        RepositoryPolicy.UPDATE_POLICY_NEVER,
                        RepositoryPolicy.UPDATE_POLICY_DAILY,
                        RepositoryPolicy.CHECKSUM_POLICY_FAIL))
                .build();
    }
}
