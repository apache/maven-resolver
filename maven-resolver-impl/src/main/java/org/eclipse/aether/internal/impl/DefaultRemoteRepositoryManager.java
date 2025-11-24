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
package org.eclipse.aether.internal.impl;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.UpdatePolicyAnalyzer;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.repository.RepositoryIdHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 */
@Singleton
@Named
public class DefaultRemoteRepositoryManager implements RemoteRepositoryManager {
    private static final String CONFIG_PROPS_PREFIX =
            ConfigurationProperties.PREFIX_AETHER + "remoteRepositoryManager.";

    /**
     * <b>Experimental:</b> Configuration for "repository key" function.
     * Note: repository key functions other than "nid" produce repository keys will be <em>way different
     * that those produced with previous versions or without this option enabled</em>. Manager uses this key to
     * detect "same" remote repositories, and in case of mirrors, to merge them.
     *
     * @since 2.0.14
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_REPOSITORY_KEY_FUNCTION}
     */
    public static final String CONFIG_PROP_REPOSITORY_KEY_FUNCTION = CONFIG_PROPS_PREFIX + "repositoryKeyFunction";

    public static final String DEFAULT_REPOSITORY_KEY_FUNCTION = "nid";

    /**
     * Method that based on configuration returns the "repository key function". Used by {@link EnhancedLocalRepositoryManagerFactory}
     * and {@link LocalPathPrefixComposerFactory}.
     *
     * @since 2.0.14
     */
    @SuppressWarnings("unchecked")
    private static BiFunction<RemoteRepository, String, String> repositoryKeyFunction(RepositorySystemSession session) {
        final RepositoryIdHelper.RepositoryKeyFunction repositoryKeyFunction =
                RepositoryIdHelper.getRepositoryKeyFunction(ConfigUtils.getString(
                        session, DEFAULT_REPOSITORY_KEY_FUNCTION, CONFIG_PROP_REPOSITORY_KEY_FUNCTION));
        if (session.getCache() != null) {
            // both are expensive methods; cache it in session (repo -> context -> ID)
            return (repository, context) -> ((ConcurrentMap<RemoteRepository, ConcurrentMap<String, String>>)
                            session.getCache()
                                    .computeIfAbsent(
                                            session,
                                            EnhancedLocalRepositoryManagerFactory.class.getName()
                                                    + ".repositoryKeyFunction",
                                            ConcurrentHashMap::new))
                    .computeIfAbsent(repository, k1 -> new ConcurrentHashMap<>())
                    .computeIfAbsent(
                            context == null ? "" : context, k2 -> repositoryKeyFunction.apply(repository, context));
        } else {
            return repositoryKeyFunction;
        }
    }

    private static final class LoggedMirror {

        private final Object[] keys;

        LoggedMirror(RemoteRepository original, RemoteRepository mirror) {
            keys = new Object[] {mirror.getId(), mirror.getUrl(), original.getId(), original.getUrl()};
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof LoggedMirror)) {
                return false;
            }
            LoggedMirror that = (LoggedMirror) obj;
            return Arrays.equals(keys, that.keys);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(keys);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRemoteRepositoryManager.class);

    private final UpdatePolicyAnalyzer updatePolicyAnalyzer;

    private final ChecksumPolicyProvider checksumPolicyProvider;

    @Inject
    public DefaultRemoteRepositoryManager(
            UpdatePolicyAnalyzer updatePolicyAnalyzer, ChecksumPolicyProvider checksumPolicyProvider) {
        this.updatePolicyAnalyzer = requireNonNull(updatePolicyAnalyzer, "update policy analyzer cannot be null");
        this.checksumPolicyProvider = requireNonNull(checksumPolicyProvider, "checksum policy provider cannot be null");
    }

    @Override
    public List<RemoteRepository> aggregateRepositories(
            RepositorySystemSession session,
            List<RemoteRepository> dominantRepositories,
            List<RemoteRepository> recessiveRepositories,
            boolean recessiveIsRaw) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(dominantRepositories, "dominantRepositories cannot be null");
        requireNonNull(recessiveRepositories, "recessiveRepositories cannot be null");
        if (recessiveRepositories.isEmpty()) {
            return dominantRepositories;
        }

        BiFunction<RemoteRepository, String, String> repositoryKeyFunction = repositoryKeyFunction(session);
        MirrorSelector mirrorSelector = session.getMirrorSelector();
        AuthenticationSelector authSelector = session.getAuthenticationSelector();
        ProxySelector proxySelector = session.getProxySelector();

        List<RemoteRepository> result = new ArrayList<>(dominantRepositories);

        next:
        for (RemoteRepository recessiveRepository : recessiveRepositories) {
            RemoteRepository repository = recessiveRepository;

            if (recessiveIsRaw) {
                RemoteRepository mirrorRepository = mirrorSelector.getMirror(recessiveRepository);

                if (mirrorRepository != null) {
                    logMirror(session, recessiveRepository, mirrorRepository);
                    repository = mirrorRepository;
                }
            }

            String key = repositoryKeyFunction.apply(repository, null);

            for (ListIterator<RemoteRepository> it = result.listIterator(); it.hasNext(); ) {
                RemoteRepository dominantRepository = it.next();

                if (key.equals(repositoryKeyFunction.apply(dominantRepository, null))) {
                    if (!dominantRepository.getMirroredRepositories().isEmpty()
                            && !repository.getMirroredRepositories().isEmpty()) {
                        RemoteRepository mergedRepository =
                                mergeMirrors(session, repositoryKeyFunction, dominantRepository, repository);
                        if (mergedRepository != dominantRepository) {
                            it.set(mergedRepository);
                        }
                    }

                    continue next;
                }
            }

            if (recessiveIsRaw) {
                RemoteRepository.Builder builder = null;
                Authentication auth = authSelector.getAuthentication(repository);
                if (auth != null) {
                    builder = new RemoteRepository.Builder(repository);
                    builder.setAuthentication(auth);
                }
                Proxy proxy = proxySelector.getProxy(repository);
                if (proxy != null) {
                    if (builder == null) {
                        builder = new RemoteRepository.Builder(repository);
                    }
                    builder.setProxy(proxy);
                }
                if (builder != null) {
                    repository = builder.build();
                }
            }

            result.add(repository);
        }

        return result.stream()
                .map(r -> new RemoteRepository.Builder(r)
                        .setIntent(RemoteRepository.Intent.RESOLUTION)
                        .build())
                .collect(Collectors.toList());
    }

    private void logMirror(RepositorySystemSession session, RemoteRepository original, RemoteRepository mirror) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        RepositoryCache cache = session.getCache();
        if (cache != null) {
            Object key = new LoggedMirror(original, mirror);
            if (cache.get(session, key) != null) {
                return;
            }
            cache.put(session, key, Boolean.TRUE);
        }
        LOGGER.debug(
                "Using mirror {} ({}) for {} ({}).",
                mirror.getId(),
                mirror.getUrl(),
                original.getId(),
                original.getUrl());
    }

    private RemoteRepository mergeMirrors(
            RepositorySystemSession session,
            BiFunction<RemoteRepository, String, String> repositoryKeyFunction,
            RemoteRepository dominant,
            RemoteRepository recessive) {
        RemoteRepository.Builder merged = null;
        RepositoryPolicy releases = null, snapshots = null;

        next:
        for (RemoteRepository rec : recessive.getMirroredRepositories()) {
            String recKey = repositoryKeyFunction.apply(rec, null);

            for (RemoteRepository dom : dominant.getMirroredRepositories()) {
                if (recKey.equals(repositoryKeyFunction.apply(dom, null))) {
                    continue next;
                }
            }

            if (merged == null) {
                merged = new RemoteRepository.Builder(dominant);
                releases = dominant.getPolicy(false);
                snapshots = dominant.getPolicy(true);
            }

            releases = merge(session, releases, rec.getPolicy(false), false);
            snapshots = merge(session, snapshots, rec.getPolicy(true), false);

            merged.addMirroredRepository(rec);
        }

        if (merged == null) {
            return dominant;
        }
        return merged.setReleasePolicy(releases).setSnapshotPolicy(snapshots).build();
    }

    @Override
    public RepositoryPolicy getPolicy(
            RepositorySystemSession session, RemoteRepository repository, boolean releases, boolean snapshots) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");
        RepositoryPolicy policy1 = releases ? repository.getPolicy(false) : null;
        RepositoryPolicy policy2 = snapshots ? repository.getPolicy(true) : null;
        return merge(session, policy1, policy2, true);
    }

    private RepositoryPolicy merge(
            RepositorySystemSession session, RepositoryPolicy policy1, RepositoryPolicy policy2, boolean globalPolicy) {
        RepositoryPolicy policy;

        if (policy2 == null) {
            if (globalPolicy) {
                policy = merge(
                        policy1,
                        session.getArtifactUpdatePolicy(),
                        session.getMetadataUpdatePolicy(),
                        session.getChecksumPolicy());
            } else {
                policy = policy1;
            }
        } else if (policy1 == null) {
            if (globalPolicy) {
                policy = merge(
                        policy2,
                        session.getArtifactUpdatePolicy(),
                        session.getMetadataUpdatePolicy(),
                        session.getChecksumPolicy());
            } else {
                policy = policy2;
            }
        } else if (!policy2.isEnabled()) {
            if (globalPolicy) {
                policy = merge(
                        policy1,
                        session.getArtifactUpdatePolicy(),
                        session.getMetadataUpdatePolicy(),
                        session.getChecksumPolicy());
            } else {
                policy = policy1;
            }
        } else if (!policy1.isEnabled()) {
            if (globalPolicy) {
                policy = merge(
                        policy2,
                        session.getArtifactUpdatePolicy(),
                        session.getMetadataUpdatePolicy(),
                        session.getChecksumPolicy());
            } else {
                policy = policy2;
            }
        } else {
            String checksums = session.getChecksumPolicy();
            //noinspection StatementWithEmptyBody
            if (globalPolicy && checksums != null && !checksums.isEmpty()) {
                // use global override
            } else {
                checksums = checksumPolicyProvider.getEffectiveChecksumPolicy(
                        session, policy1.getChecksumPolicy(), policy2.getChecksumPolicy());
            }

            String artifactUpdates = session.getArtifactUpdatePolicy();
            //noinspection StatementWithEmptyBody
            if (globalPolicy && artifactUpdates != null && !artifactUpdates.isEmpty()) {
                // use global override
            } else {
                artifactUpdates = updatePolicyAnalyzer.getEffectiveUpdatePolicy(
                        session, policy1.getArtifactUpdatePolicy(), policy2.getArtifactUpdatePolicy());
            }
            String metadataUpdates = session.getMetadataUpdatePolicy();
            if (globalPolicy && metadataUpdates != null && !metadataUpdates.isEmpty()) {
                // use global override
            } else {
                metadataUpdates = updatePolicyAnalyzer.getEffectiveUpdatePolicy(
                        session, policy1.getMetadataUpdatePolicy(), policy2.getMetadataUpdatePolicy());
            }

            policy = new RepositoryPolicy(true, artifactUpdates, metadataUpdates, checksums);
        }

        return policy;
    }

    private RepositoryPolicy merge(
            RepositoryPolicy policy, String artifactUpdates, String metadataUpdates, String checksums) {
        if (policy != null) {
            if (artifactUpdates == null || artifactUpdates.isEmpty()) {
                artifactUpdates = policy.getArtifactUpdatePolicy();
            }
            if (metadataUpdates == null || metadataUpdates.isEmpty()) {
                metadataUpdates = policy.getMetadataUpdatePolicy();
            }
            if (checksums == null || checksums.isEmpty()) {
                checksums = policy.getChecksumPolicy();
            }
            if (!policy.getArtifactUpdatePolicy().equals(artifactUpdates)
                    || !policy.getMetadataUpdatePolicy().equals(metadataUpdates)
                    || !policy.getChecksumPolicy().equals(checksums)) {
                policy = new RepositoryPolicy(policy.isEnabled(), artifactUpdates, metadataUpdates, checksums);
            }
        }
        return policy;
    }
}
