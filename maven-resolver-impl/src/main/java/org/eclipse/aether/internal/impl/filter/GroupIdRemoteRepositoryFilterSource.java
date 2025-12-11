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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.aether.MultiRuntimeException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.RepositorySystemLifecycle;
import org.eclipse.aether.internal.impl.filter.ruletree.GroupTree;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.spi.remoterepo.RepositoryKeyFunctionFactory;
import org.eclipse.aether.spi.resolution.ArtifactResolverPostProcessor;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Remote repository filter source filtering on G coordinate. It is backed by a file that is parsed into {@link GroupTree}.
 * <p>
 * The file can be authored manually. The file can also be pre-populated by "record" functionality of this filter.
 * When "recording", this filter will not filter out anything, but will instead populate the file with all encountered
 * groupIds recorded as {@code =groupId}. The recorded file should be authored afterward to fine tune it, as there is
 * no optimization in place (ie to look for smallest common parent groupId and alike).
 * <p>
 * The groupId file is expected on path "${basedir}/groupId-${repository.id}.txt".
 * <p>
 * The groupId file once loaded are cached in component, so in-flight groupId file change during component existence
 * are NOT noticed.
 *
 * @see GroupTree
 *
 * @since 1.9.0
 */
@Singleton
@Named(GroupIdRemoteRepositoryFilterSource.NAME)
public final class GroupIdRemoteRepositoryFilterSource extends RemoteRepositoryFilterSourceSupport
        implements ArtifactResolverPostProcessor {
    public static final String NAME = "groupId";

    /**
     * Configuration to enable the GroupId filter (enabled by default). Can be fine-tuned per repository using
     * repository ID suffixes.
     * <strong>Important:</strong> For this filter to take effect, you must provide configuration files. Without
     * configuration files, the enabled filter remains dormant and does not interfere with resolution.
     * <strong>Configuration Files:</strong>
     * <ul>
     * <li>Location: Directory specified by {@link #CONFIG_PROP_BASEDIR} (defaults to {@code $LOCAL_REPO/.remoteRepositoryFilters})</li>
     * <li>Naming: {@code groupId-$(repository.id).txt}</li>
     * <li>Content: One groupId per line to allow/block from the repository</li>
     * </ul>
     * <strong>Recommended Setup (Per-Project):</strong>
     * Use project-specific configuration to avoid repository ID clashes. Add to {@code .mvn/maven.config}:
     * <pre>
     * -Daether.remoteRepositoryFilter.groupId=true
     * -Daether.remoteRepositoryFilter.groupId.basedir=${session.rootDirectory}/.mvn/rrf/
     * </pre>
     * Then create {@code groupId-myrepoId.txt} files in the {@code .mvn/rrf/} directory and commit them to version control.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationRepoIdSuffix Yes
     * @configurationDefaultValue {@link #DEFAULT_ENABLED}
     */
    public static final String CONFIG_PROP_ENABLED = RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME;

    public static final boolean DEFAULT_ENABLED = true;

    /**
     * Configuration to skip the GroupId filter for given request. This configuration is evaluated and if {@code true}
     * the GroupId remote filter will not kick in.
     *
     * @since 2.0.14
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationRepoIdSuffix Yes
     * @configurationDefaultValue {@link #DEFAULT_SKIPPED}
     */
    public static final String CONFIG_PROP_SKIPPED =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".skipped";

    public static final boolean DEFAULT_SKIPPED = false;

    /**
     * Determines what happens when the filter is enabled, but has no groupId file available for given remote repository
     * to work with. When set to {@code true} (default), the filter allows all requests to proceed for given remote
     * repository when no groupId file is available. When set to {@code false}, the filter blocks all requests toward
     * given remote repository when no groupId file is available. This setting allows repoId suffix, hence, can
     * determine "global" or "repository targeted" behaviors.
     *
     * @since 2.0.14
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationRepoIdSuffix Yes
     * @configurationDefaultValue {@link #DEFAULT_NO_INPUT_OUTCOME}
     */
    public static final String CONFIG_PROP_NO_INPUT_OUTCOME =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".noInputOutcome";

    public static final boolean DEFAULT_NO_INPUT_OUTCOME = true;

    /**
     * The basedir where to store filter files. If path is relative, it is resolved from local repository root.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #LOCAL_REPO_PREFIX_DIR}
     */
    public static final String CONFIG_PROP_BASEDIR =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".basedir";

    public static final String LOCAL_REPO_PREFIX_DIR = ".remoteRepositoryFilters";

    /**
     * Should filter go into "record" mode (and collect encountered artifacts)?
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue false
     */
    public static final String CONFIG_PROP_RECORD =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".record";

    static final String GROUP_ID_FILE_PREFIX = "groupId-";

    static final String GROUP_ID_FILE_SUFFIX = ".txt";

    private final Logger logger = LoggerFactory.getLogger(GroupIdRemoteRepositoryFilterSource.class);

    private final RepositorySystemLifecycle repositorySystemLifecycle;

    private final PathProcessor pathProcessor;

    @Inject
    public GroupIdRemoteRepositoryFilterSource(
            RepositoryKeyFunctionFactory repositoryKeyFunctionFactory,
            RepositorySystemLifecycle repositorySystemLifecycle,
            PathProcessor pathProcessor) {
        super(repositoryKeyFunctionFactory);
        this.repositorySystemLifecycle = requireNonNull(repositorySystemLifecycle);
        this.pathProcessor = requireNonNull(pathProcessor);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<RemoteRepository, GroupTree> rules(RepositorySystemSession session) {
        return (ConcurrentMap<RemoteRepository, GroupTree>)
                session.getData().computeIfAbsent(getClass().getName() + ".rules", ConcurrentHashMap::new);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<RemoteRepository, Path> ruleFiles(RepositorySystemSession session) {
        return (ConcurrentMap<RemoteRepository, Path>)
                session.getData().computeIfAbsent(getClass().getName() + ".ruleFiles", ConcurrentHashMap::new);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<RemoteRepository, Set<String>> recordedRules(RepositorySystemSession session) {
        return (ConcurrentMap<RemoteRepository, Set<String>>)
                session.getData().computeIfAbsent(getClass().getName() + ".recordedRules", ConcurrentHashMap::new);
    }

    private AtomicBoolean onShutdownHandlerRegistered(RepositorySystemSession session) {
        return (AtomicBoolean) session.getData()
                .computeIfAbsent(getClass().getName() + ".onShutdownHandlerRegistered", AtomicBoolean::new);
    }

    @Override
    protected boolean isEnabled(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, DEFAULT_ENABLED, CONFIG_PROP_ENABLED)
                && !ConfigUtils.getBoolean(session, DEFAULT_SKIPPED, CONFIG_PROP_SKIPPED);
    }

    private boolean isRepositoryFilteringEnabled(RepositorySystemSession session, RemoteRepository remoteRepository) {
        if (isEnabled(session)) {
            return ConfigUtils.getBoolean(
                            session,
                            DEFAULT_ENABLED,
                            CONFIG_PROP_ENABLED + "." + remoteRepository.getId(),
                            CONFIG_PROP_ENABLED + ".*")
                    && !ConfigUtils.getBoolean(
                            session,
                            DEFAULT_SKIPPED,
                            CONFIG_PROP_SKIPPED + "." + remoteRepository.getId(),
                            CONFIG_PROP_SKIPPED + ".*");
        }
        return false;
    }

    @Override
    public RemoteRepositoryFilter getRemoteRepositoryFilter(RepositorySystemSession session) {
        if (isEnabled(session) && !isRecord(session)) {
            return new GroupIdFilter(session);
        }
        return null;
    }

    @Override
    public void postProcess(RepositorySystemSession session, List<ArtifactResult> artifactResults) {
        if (isEnabled(session) && isRecord(session)) {
            if (onShutdownHandlerRegistered(session).compareAndSet(false, true)) {
                repositorySystemLifecycle.addOnSystemEndedHandler(() -> saveRecordedLines(session));
            }
            for (ArtifactResult artifactResult : artifactResults) {
                if (artifactResult.isResolved() && artifactResult.getRepository() instanceof RemoteRepository) {
                    RemoteRepository remoteRepository = (RemoteRepository) artifactResult.getRepository();
                    if (isRepositoryFilteringEnabled(session, remoteRepository)) {
                        ruleFile(session, remoteRepository); // populate it; needed for save
                        String line = "=" + artifactResult.getArtifact().getGroupId();
                        RemoteRepository normalized = normalizeRemoteRepository(session, remoteRepository);
                        recordedRules(session)
                                .computeIfAbsent(normalized, k -> new TreeSet<>())
                                .add(line);
                        rules(session)
                                .compute(normalized, (k, v) -> {
                                    if (v == null || v == DISABLED || v == ENABLED_NO_INPUT) {
                                        v = new GroupTree("");
                                    }
                                    return v;
                                })
                                .loadNode(line);
                    }
                }
            }
        }
    }

    private Path ruleFile(RepositorySystemSession session, RemoteRepository remoteRepository) {
        return ruleFiles(session).computeIfAbsent(normalizeRemoteRepository(session, remoteRepository), r -> getBasedir(
                        session, LOCAL_REPO_PREFIX_DIR, CONFIG_PROP_BASEDIR, false)
                .resolve(GROUP_ID_FILE_PREFIX + repositoryKey(session, remoteRepository) + GROUP_ID_FILE_SUFFIX));
    }

    private GroupTree cacheRules(RepositorySystemSession session, RemoteRepository remoteRepository) {
        return rules(session)
                .computeIfAbsent(
                        normalizeRemoteRepository(session, remoteRepository), r -> loadRepositoryRules(session, r));
    }

    private static final GroupTree DISABLED = new GroupTree("disabled");
    private static final GroupTree ENABLED_NO_INPUT = new GroupTree("enabled-no-input");

    private GroupTree loadRepositoryRules(RepositorySystemSession session, RemoteRepository remoteRepository) {
        if (isRepositoryFilteringEnabled(session, remoteRepository)) {
            Path filePath = ruleFile(session, remoteRepository);
            if (Files.isReadable(filePath)) {
                try (Stream<String> lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
                    GroupTree groupTree = new GroupTree("");
                    int rules = groupTree.loadNodes(lines);
                    logger.info("Loaded {} group rules for remote repository {}", rules, remoteRepository.getId());
                    if (logger.isDebugEnabled()) {
                        groupTree.dump("");
                    }
                    return groupTree;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            logger.debug("Group rules file for remote repository {} not available", remoteRepository);
            return ENABLED_NO_INPUT;
        }
        logger.debug("Group rules file for remote repository {} disabled", remoteRepository);
        return DISABLED;
    }

    private class GroupIdFilter implements RemoteRepositoryFilter {
        private final RepositorySystemSession session;

        private GroupIdFilter(RepositorySystemSession session) {
            this.session = session;
        }

        @Override
        public Result acceptArtifact(RemoteRepository repository, Artifact artifact) {
            return acceptGroupId(repository, artifact.getGroupId());
        }

        @Override
        public Result acceptMetadata(RemoteRepository repository, Metadata metadata) {
            return acceptGroupId(repository, metadata.getGroupId());
        }

        private Result acceptGroupId(RemoteRepository repository, String groupId) {
            GroupTree groupTree = cacheRules(session, repository);
            if (groupTree == DISABLED) {
                return result(true, NAME, "Disabled");
            } else if (groupTree == ENABLED_NO_INPUT) {
                return result(
                        ConfigUtils.getBoolean(
                                session,
                                DEFAULT_NO_INPUT_OUTCOME,
                                CONFIG_PROP_NO_INPUT_OUTCOME + "." + repository.getId(),
                                CONFIG_PROP_NO_INPUT_OUTCOME),
                        NAME,
                        "No input available");
            }

            boolean accepted = groupTree.acceptedGroupId(groupId);
            return result(
                    accepted,
                    NAME,
                    accepted
                            ? "G:" + groupId + " allowed from " + repository.getId()
                            : "G:" + groupId + " NOT allowed from " + repository.getId());
        }
    }

    /**
     * Returns {@code true} if given session is recording.
     */
    private boolean isRecord(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, false, CONFIG_PROP_RECORD);
    }

    /**
     * On-close handler that saves recorded rules, if any.
     */
    private void saveRecordedLines(RepositorySystemSession session) {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (Map.Entry<RemoteRepository, Path> entry : ruleFiles(session).entrySet()) {
            Set<String> recorded = recordedRules(session).get(entry.getKey());
            if (recorded != null && !recorded.isEmpty()) {
                try {
                    ArrayList<String> result = new ArrayList<>();
                    if (Files.isReadable(entry.getValue())) {
                        result.addAll(Files.readAllLines(entry.getValue()));
                    }
                    result.add("# Recorded entries");
                    result.addAll(recorded);
                    logger.info("Saving {} groupIds to '{}'", result.size(), entry.getValue());
                    pathProcessor.writeWithBackup(
                            entry.getValue(), result.stream().collect(Collectors.joining(System.lineSeparator())));
                } catch (IOException e) {
                    exceptions.add(e);
                }
            }
        }
        MultiRuntimeException.mayThrow("session save groupIds failure", exceptions);
    }
}
