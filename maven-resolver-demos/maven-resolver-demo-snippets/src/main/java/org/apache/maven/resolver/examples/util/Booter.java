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
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
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
import org.eclipse.aether.supplier.SessionBuilderSupplier;
import org.eclipse.aether.util.graph.visitor.DependencyGraphDumper;

/**
 * A helper to boot the repository system and a repository system session.
 */
public class Booter {
    public static final String SUPPLIER = "supplier";

    public static final String SISU = "sisu";

    public static final DependencyGraphDumper DUMPER_SOUT = new DependencyGraphDumper(System.out::println);

    public static String selectFactory(String[] args) {
        if (args == null || args.length == 0) {
            return SUPPLIER;
        } else {
            return args[0];
        }
    }

    public static RepositorySystem newRepositorySystem(final String factory) {
        switch (factory) {
            case SUPPLIER:
                return org.apache.maven.resolver.examples.supplier.SupplierRepositorySystemFactory
                        .newRepositorySystem();
            case SISU:
                return org.apache.maven.resolver.examples.sisu.SisuRepositorySystemFactory.newRepositorySystem();
            default:
                throw new IllegalArgumentException("Unknown factory: " + factory);
        }
    }

    public static SessionBuilder newRepositorySystemSession(RepositorySystem system) {
        FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
        SessionBuilder result = new SessionBuilderSupplier(system)
                .get()
                .setSystemProperties(System.getProperties())
                .withLocalRepositoryBaseDirectories(fs.getPath("local-repo"))
                .setRepositoryListener(new ConsoleRepositoryListener())
                .setTransferListener(new ConsoleTransferListener())
                .setConfigProperty("aether.generator.gpg.enabled", Boolean.TRUE.toString())
                .setConfigProperty(
                        "aether.generator.gpg.keyFilePath",
                        Paths.get("src/main/resources/alice.key")
                                .toAbsolutePath()
                                .toString())
                .setConfigProperty("aether.syncContext.named.factory", "noop");
        result.addOnSessionEndedHandler(() -> {
            try {
                fs.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // uncomment to generate dirty trees
        // session.setDependencyGraphTransformer( null );

        return result;
    }

    public static List<RemoteRepository> newRepositories(RepositorySystem system, RepositorySystemSession session) {
        return new ArrayList<>(Collections.singletonList(newCentralRepository()));
    }

    private static RemoteRepository newCentralRepository() {
        return new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build();
    }
}
