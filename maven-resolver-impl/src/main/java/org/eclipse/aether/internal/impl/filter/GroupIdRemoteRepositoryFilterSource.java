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
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.eclipse.aether.spi.resolution.ArtifactResolverPostProcessor;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.FileUtils;
import org.eclipse.aether.util.repository.RepositoryIdHelper;
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

    private static final String CONFIG_PROPS_PREFIX =
            RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".";

    /**
     * Is filter enabled? Filter must be enabled, and can be "fine-tuned" by repository id appended properties.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationRepoIdSuffix Yes
     * @configurationDefaultValue {@link #DEFAULT_ENABLED}
     */
    public static final String CONFIG_PROP_ENABLED = RemoteRepositoryFilterSourceSupport.CONFIG_PROPS_PREFIX + NAME;

    public static final boolean DEFAULT_ENABLED = false;

    /**
     * The basedir where to store filter files. If path is relative, it is resolved from local repository root.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #LOCAL_REPO_PREFIX_DIR}
     */
    public static final String CONFIG_PROP_BASEDIR = CONFIG_PROPS_PREFIX + "basedir";

    public static final String LOCAL_REPO_PREFIX_DIR = ".remoteRepositoryFilters";

    /**
     * Should filter go into "record" mode (and collect encountered artifacts)?
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue false
     */
    public static final String CONFIG_PROP_RECORD = CONFIG_PROPS_PREFIX + "record";

    static final String GROUP_ID_FILE_PREFIX = "groupId-";

    static final String GROUP_ID_FILE_SUFFIX = ".txt";

    private final Logger logger = LoggerFactory.getLogger(GroupIdRemoteRepositoryFilterSource.class);

    private final RepositorySystemLifecycle repositorySystemLifecycle;

    private final ConcurrentHashMap<RemoteRepository, GroupTree> rules;

    private final ConcurrentHashMap<RemoteRepository, Path> ruleFiles;

    private final ConcurrentHashMap<RemoteRepository, Set<String>> recordedRules;

    private final AtomicBoolean onShutdownHandlerRegistered;

    @Inject
    public GroupIdRemoteRepositoryFilterSource(RepositorySystemLifecycle repositorySystemLifecycle) {
        this.repositorySystemLifecycle = requireNonNull(repositorySystemLifecycle);
        this.rules = new ConcurrentHashMap<>();
        this.ruleFiles = new ConcurrentHashMap<>();
        this.recordedRules = new ConcurrentHashMap<>();
        this.onShutdownHandlerRegistered = new AtomicBoolean(false);
    }

    @Override
    protected boolean isEnabled(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, DEFAULT_ENABLED, CONFIG_PROP_ENABLED);
    }

    private boolean isRepositoryFilteringEnabled(RepositorySystemSession session, RemoteRepository remoteRepository) {
        if (isEnabled(session)) {
            return ConfigUtils.getBoolean(
                    session,
                    ConfigUtils.getBoolean(session, true, CONFIG_PROP_ENABLED + ".*"),
                    CONFIG_PROP_ENABLED + "." + remoteRepository.getId());
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
            if (onShutdownHandlerRegistered.compareAndSet(false, true)) {
                repositorySystemLifecycle.addOnSystemEndedHandler(this::saveRecordedLines);
            }
            for (ArtifactResult artifactResult : artifactResults) {
                if (artifactResult.isResolved() && artifactResult.getRepository() instanceof RemoteRepository) {
                    RemoteRepository remoteRepository = (RemoteRepository) artifactResult.getRepository();
                    if (isRepositoryFilteringEnabled(session, remoteRepository)) {
                        ruleFile(session, remoteRepository); // populate it; needed for save
                        String line = "=" + artifactResult.getArtifact().getGroupId();
                        recordedRules
                                .computeIfAbsent(remoteRepository, k -> new TreeSet<>())
                                .add(line);
                        rules.compute(remoteRepository, (k, v) -> {
                                    if (v == null || v == GroupTree.SENTINEL) {
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
        return ruleFiles.computeIfAbsent(remoteRepository, r -> getBasedir(
                        session, LOCAL_REPO_PREFIX_DIR, CONFIG_PROP_BASEDIR, false)
                .resolve(GROUP_ID_FILE_PREFIX
                        + RepositoryIdHelper.cachedIdToPathSegment(session).apply(remoteRepository)
                        + GROUP_ID_FILE_SUFFIX));
    }

    private GroupTree cacheRules(RepositorySystemSession session, RemoteRepository remoteRepository) {
        return rules.computeIfAbsent(remoteRepository, r -> loadRepositoryRules(session, r));
    }

    private GroupTree loadRepositoryRules(RepositorySystemSession session, RemoteRepository remoteRepository) {
        Path filePath = ruleFile(session, remoteRepository);
        if (isRepositoryFilteringEnabled(session, remoteRepository) && Files.isReadable(filePath)) {
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
        return GroupTree.SENTINEL;
    }

    private class GroupIdFilter implements RemoteRepositoryFilter {
        private final RepositorySystemSession session;

        private GroupIdFilter(RepositorySystemSession session) {
            this.session = session;
        }

        @Override
        public Result acceptArtifact(RemoteRepository remoteRepository, Artifact artifact) {
            return acceptGroupId(remoteRepository, artifact.getGroupId());
        }

        @Override
        public Result acceptMetadata(RemoteRepository remoteRepository, Metadata metadata) {
            return acceptGroupId(remoteRepository, metadata.getGroupId());
        }

        private Result acceptGroupId(RemoteRepository remoteRepository, String groupId) {
            GroupTree groupTree = cacheRules(session, remoteRepository);
            if (GroupTree.SENTINEL == groupTree) {
                return NOT_PRESENT_RESULT;
            }

            if (groupTree.acceptedGroupId(groupId)) {
                return new SimpleResult(true, "G:" + groupId + " allowed from " + remoteRepository);
            } else {
                return new SimpleResult(false, "G:" + groupId + " NOT allowed from " + remoteRepository);
            }
        }
    }

    /**
     * Filter result when filter "stands aside" as it had no input.
     */
    private static final RemoteRepositoryFilter.Result NOT_PRESENT_RESULT =
            new SimpleResult(true, "GroupId file not present");

    /**
     * Returns {@code true} if given session is recording.
     */
    private boolean isRecord(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, false, CONFIG_PROP_RECORD);
    }

    /**
     * On-close handler that saves recorded rules, if any.
     */
    private void saveRecordedLines() {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (Map.Entry<RemoteRepository, Path> entry : ruleFiles.entrySet()) {
            Set<String> recorded = recordedRules.get(entry.getKey());
            if (recorded != null && !recorded.isEmpty()) {
                try {
                    ArrayList<String> result = new ArrayList<>();
                    if (Files.isReadable(entry.getValue())) {
                        result.addAll(Files.readAllLines(entry.getValue()));
                    }
                    result.add("# Recorded entries");
                    result.addAll(recorded);
                    logger.info("Saving {} groupIds to '{}'", result.size(), entry.getValue());
                    FileUtils.writeFileWithBackup(entry.getValue(), p -> Files.write(p, result));
                } catch (IOException e) {
                    exceptions.add(e);
                }
            }
        }
        MultiRuntimeException.mayThrow("session save groupIds failure", exceptions);
    }
}
