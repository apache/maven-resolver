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
package org.eclipse.aether.internal.impl.filter;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * Remote repository filter source filtering on path prefixes. It is backed by a file that lists all allowed path
 * prefixes from remote repository. Artifact that layout converted path (using remote repository layout) results in
 * path with no corresponding prefix present in this file is filtered out.
 * <p>
 * The file can be authored manually: format is one prefix per line, comments starting with "#" (hash) and empty lines
 * for structuring are supported, The "/" (slash) character is used as file separator. Some remote repositories and
 * MRMs publish these kind of files, they can be downloaded from corresponding URLs.
 * <p>
 * The prefix file is expected on path "${basedir}/prefixes-${repository.id}.txt".
 * <p>
 * The prefixes file is once loaded and cached, so in-flight prefixes file change during component existence are not
 * noticed.
 * <p>
 * Examples of published prefix files:
 * <ul>
 *     <li>Central: <a href="https://repo.maven.apache.org/maven2/.meta/prefixes.txt">prefixes.txt</a></li>
 *     <li>Apache Releases:
 *     <a href="https://repository.apache.org/content/repositories/releases/.meta/prefixes.txt">prefixes.txt</a></li>
 * </ul>
 *
 * @since 1.9.0
 */
@Singleton
@Named(PrefixesRemoteRepositoryFilterSource.NAME)
public final class PrefixesRemoteRepositoryFilterSource extends RemoteRepositoryFilterSourceSupport {
    public static final String NAME = "prefixes";

    private static final String CONFIG_PROPS_PREFIX =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".";

    /**
     * Is filter enabled?
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue false
     */
    public static final String CONFIG_PROP_ENABLED = RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME;

    /**
     * The basedir where to store filter files. If path is relative, it is resolved from local repository root.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #LOCAL_REPO_PREFIX_DIR}
     */
    public static final String CONFIG_PROP_BASEDIR = CONFIG_PROPS_PREFIX + "basedir";

    public static final String LOCAL_REPO_PREFIX_DIR = ".remoteRepositoryFilters";

    static final String PREFIXES_FILE_PREFIX = "prefixes-";

    static final String PREFIXES_FILE_SUFFIX = ".txt";

    private static final Logger LOGGER = LoggerFactory.getLogger(PrefixesRemoteRepositoryFilterSource.class);

    private final RepositoryLayoutProvider repositoryLayoutProvider;

    private final ConcurrentHashMap<RemoteRepository, Node> prefixes;

    private final ConcurrentHashMap<RemoteRepository, RepositoryLayout> layouts;

    @Inject
    public PrefixesRemoteRepositoryFilterSource(RepositoryLayoutProvider repositoryLayoutProvider) {
        this.repositoryLayoutProvider = requireNonNull(repositoryLayoutProvider);
        this.prefixes = new ConcurrentHashMap<>();
        this.layouts = new ConcurrentHashMap<>();
    }

    @Override
    protected boolean isEnabled(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, false, CONFIG_PROP_ENABLED);
    }

    @Override
    public RemoteRepositoryFilter getRemoteRepositoryFilter(RepositorySystemSession session) {
        if (isEnabled(session)) {
            return new PrefixesFilter(session, getBasedir(session, LOCAL_REPO_PREFIX_DIR, CONFIG_PROP_BASEDIR, false));
        }
        return null;
    }

    /**
     * Caches layout instances for remote repository. In case of unknown layout it returns {@code null}.
     *
     * @return the layout instance of {@code null} if layout not supported.
     */
    private RepositoryLayout cacheLayout(RepositorySystemSession session, RemoteRepository remoteRepository) {
        return layouts.computeIfAbsent(remoteRepository, r -> {
            try {
                return repositoryLayoutProvider.newRepositoryLayout(session, remoteRepository);
            } catch (NoRepositoryLayoutException e) {
                return null;
            }
        });
    }

    /**
     * Caches prefixes instances for remote repository.
     */
    private Node cacheNode(Path basedir, RemoteRepository remoteRepository) {
        return prefixes.computeIfAbsent(remoteRepository, r -> loadRepositoryPrefixes(basedir, remoteRepository));
    }

    /**
     * Loads prefixes file and preprocesses it into {@link Node} instance.
     */
    private Node loadRepositoryPrefixes(Path baseDir, RemoteRepository remoteRepository) {
        Path filePath = baseDir.resolve(PREFIXES_FILE_PREFIX + remoteRepository.getId() + PREFIXES_FILE_SUFFIX);
        if (Files.isReadable(filePath)) {
            try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                LOGGER.debug(
                        "Loading prefixes for remote repository {} from file '{}'", remoteRepository.getId(), filePath);
                Node root = new Node("");
                String prefix;
                int lines = 0;
                while ((prefix = reader.readLine()) != null) {
                    if (!prefix.startsWith("#") && !prefix.trim().isEmpty()) {
                        lines++;
                        Node currentNode = root;
                        for (String element : elementsOf(prefix)) {
                            currentNode = currentNode.addSibling(element);
                        }
                    }
                }
                LOGGER.info("Loaded {} prefixes for remote repository {}", lines, remoteRepository.getId());
                return root;
            } catch (FileNotFoundException e) {
                // strange: we tested for it above, still, we should not fail
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        LOGGER.debug("Prefix file for remote repository {} not found at '{}'", remoteRepository, filePath);
        return NOT_PRESENT_NODE;
    }

    private class PrefixesFilter implements RemoteRepositoryFilter {
        private final RepositorySystemSession session;

        private final Path basedir;

        private PrefixesFilter(RepositorySystemSession session, Path basedir) {
            this.session = session;
            this.basedir = basedir;
        }

        @Override
        public Result acceptArtifact(RemoteRepository remoteRepository, Artifact artifact) {
            RepositoryLayout repositoryLayout = cacheLayout(session, remoteRepository);
            if (repositoryLayout == null) {
                return new SimpleResult(true, "Unsupported layout: " + remoteRepository);
            }
            return acceptPrefix(
                    remoteRepository,
                    repositoryLayout.getLocation(artifact, false).getPath());
        }

        @Override
        public Result acceptMetadata(RemoteRepository remoteRepository, Metadata metadata) {
            RepositoryLayout repositoryLayout = cacheLayout(session, remoteRepository);
            if (repositoryLayout == null) {
                return new SimpleResult(true, "Unsupported layout: " + remoteRepository);
            }
            return acceptPrefix(
                    remoteRepository,
                    repositoryLayout.getLocation(metadata, false).getPath());
        }

        private Result acceptPrefix(RemoteRepository remoteRepository, String path) {
            Node root = cacheNode(basedir, remoteRepository);
            if (NOT_PRESENT_NODE == root) {
                return NOT_PRESENT_RESULT;
            }
            List<String> prefix = new ArrayList<>();
            final List<String> pathElements = elementsOf(path);
            Node currentNode = root;
            for (String pathElement : pathElements) {
                prefix.add(pathElement);
                currentNode = currentNode.getSibling(pathElement);
                if (currentNode == null || currentNode.isLeaf()) {
                    break;
                }
            }
            if (currentNode != null && currentNode.isLeaf()) {
                return new SimpleResult(
                        true, "Prefix " + String.join("/", prefix) + " allowed from " + remoteRepository);
            } else {
                return new SimpleResult(
                        false, "Prefix " + String.join("/", prefix) + " NOT allowed from " + remoteRepository);
            }
        }
    }

    private static final Node NOT_PRESENT_NODE = new Node("not-present-node");

    private static final RemoteRepositoryFilter.Result NOT_PRESENT_RESULT =
            new SimpleResult(true, "Prefix file not present");

    private static class Node {
        private final String name;

        private final HashMap<String, Node> siblings;

        private Node(String name) {
            this.name = name;
            this.siblings = new HashMap<>();
        }

        public String getName() {
            return name;
        }

        public boolean isLeaf() {
            return siblings.isEmpty();
        }

        public Node addSibling(String name) {
            Node sibling = siblings.get(name);
            if (sibling == null) {
                sibling = new Node(name);
                siblings.put(name, sibling);
            }
            return sibling;
        }

        public Node getSibling(String name) {
            return siblings.get(name);
        }
    }

    private static List<String> elementsOf(final String path) {
        return Arrays.stream(path.split("/")).filter(e -> !e.isEmpty()).collect(toList());
    }
}
