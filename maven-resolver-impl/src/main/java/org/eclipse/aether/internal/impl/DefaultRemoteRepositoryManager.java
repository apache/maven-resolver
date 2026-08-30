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
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

import org.eclipse.aether.Keys;
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
import org.eclipse.aether.repository.RepositoryKeyFunction;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.remoterepo.RepositoryKeyFunctionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 */
@Singleton
@Named
public class DefaultRemoteRepositoryManager implements RemoteRepositoryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRemoteRepositoryManager.class);

    private final UpdatePolicyAnalyzer updatePolicyAnalyzer;

    private final ChecksumPolicyProvider checksumPolicyProvider;

    private final RepositoryKeyFunctionFactory repositoryKeyFunctionFactory;

    @Inject
    public DefaultRemoteRepositoryManager(
            UpdatePolicyAnalyzer updatePolicyAnalyzer,
            ChecksumPolicyProvider checksumPolicyProvider,
            RepositoryKeyFunctionFactory repositoryKeyFunctionFactory) {
        this.updatePolicyAnalyzer = requireNonNull(updatePolicyAnalyzer, "update policy analyzer cannot be null");
        this.checksumPolicyProvider = requireNonNull(checksumPolicyProvider, "checksum policy provider cannot be null");
        this.repositoryKeyFunctionFactory =
                requireNonNull(repositoryKeyFunctionFactory, "repository key function factory cannot be null");
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

        RepositoryKeyFunction repositoryKeyFunction = repositoryKeyFunctionFactory.systemRepositoryKeyFunction(session);
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
                        RemoteRepository mergedRepository = mergeMirrors(
                                session, repositoryKeyFunction, dominantRepository, repository, recessiveIsRaw);
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
            Object key = Keys.of(mirror.getId(), mirror.getUrl(), original.getId(), original.getUrl());
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

    /**
     * Flag indicating whether a repository declared by a remote artifact descriptor (POM) may weaken the checksum
     * policy of the operator-defined mirror it is merged into. When disabled (the default), the effective checksum
     * policy of a mirror never becomes weaker than what the mirror itself declares for the same nature: a recessive
     * raw repository may still enable a nature or influence update policies, but a weaker checksum policy (e.g.
     * {@code <checksumPolicy>ignore</checksumPolicy>} in a transitive POM) is not honored and a warning is logged
     * instead. Enabling this restores the legacy weakest-wins merge, which let any POM in the dependency graph
     * degrade or switch off checksum verification for downloads routed through the mirror.
     *
     * @since 2.0.22
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_RAW_CHECKSUM_POLICY_DOWNGRADE}
     */
    public static final String CONFIG_PROP_RAW_CHECKSUM_POLICY_DOWNGRADE =
            "aether.remoteRepositoryManager.rawRepositoryChecksumPolicyDowngrade";

    public static final boolean DEFAULT_RAW_CHECKSUM_POLICY_DOWNGRADE = false;

    private static RepositoryPolicy clampChecksumPolicy(
            RepositoryPolicy merged, RepositoryPolicy floor, RemoteRepository rec) {
        if (merged == null || floor == null) {
            return merged;
        }
        if (checksumPolicyRank(merged.getChecksumPolicy()) < checksumPolicyRank(floor.getChecksumPolicy())) {
            LOGGER.warn(
                    "Ignoring checksum policy '{}' contributed by repository {} ({}) declared by a remote artifact"
                            + " descriptor: it would downgrade the checksum policy '{}' of the mirror serving it;"
                            + " set {}=true to restore the legacy weakest-wins merge",
                    merged.getChecksumPolicy(),
                    rec.getId(),
                    rec.getUrl(),
                    floor.getChecksumPolicy(),
                    CONFIG_PROP_RAW_CHECKSUM_POLICY_DOWNGRADE);
            return new RepositoryPolicy(
                    merged.isEnabled(),
                    merged.getArtifactUpdatePolicy(),
                    merged.getMetadataUpdatePolicy(),
                    floor.getChecksumPolicy());
        }
        return merged;
    }

    private static int checksumPolicyRank(String policy) {
        if (policy == null) {
            return -1;
        }
        switch (policy) {
            case RepositoryPolicy.CHECKSUM_POLICY_FAIL:
                return 2;
            case RepositoryPolicy.CHECKSUM_POLICY_WARN:
                return 1;
            case RepositoryPolicy.CHECKSUM_POLICY_IGNORE:
                return 0;
            default:
                // unknown (potentially attacker-supplied) values rank below any known policy, so they can never
                // displace an operator-configured one
                return -1;
        }
    }

    private RemoteRepository mergeMirrors(
            RepositorySystemSession session,
            RepositoryKeyFunction repositoryKeyFunction,
            RemoteRepository dominant,
            RemoteRepository recessive,
            boolean recessiveIsRaw) {
        boolean rawChecksumPolicyDowngrade = org.eclipse.aether.util.ConfigUtils.getBoolean(
                session, DEFAULT_RAW_CHECKSUM_POLICY_DOWNGRADE, CONFIG_PROP_RAW_CHECKSUM_POLICY_DOWNGRADE);
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

            if (recessiveIsRaw && !rawChecksumPolicyDowngrade) {
                // "recessive" originates from a remote artifact descriptor (POM): remotely supplied input must
                // never weaken the checksum policy the operator configured on the mirror itself
                releases = clampChecksumPolicy(releases, dominant.getPolicy(false), rec);
                snapshots = clampChecksumPolicy(snapshots, dominant.getPolicy(true), rec);
            }

            merged.addMirroredRepository(rec);
        }

        if (merged == null) {
            return dominant;
        }
        return merged.setReleasePolicy(releases).setSnapshotPolicy(snapshots).build();
    }

    /**
     * Flag indicating whether the merge of a repository's release and snapshot policies (used when a single
     * effective policy has to serve both natures, most notably for metadata of nature RELEASE_OR_SNAPSHOT such as
     * {@code maven-metadata.xml} version lists) may pick the <em>weaker</em> of the two checksum policies, which was
     * the legacy behavior. When disabled (the default), the stronger of the two checksum policies wins, so enabling
     * snapshots with a lenient checksum policy no longer silently downgrades checksum enforcement below what the
     * operator configured for releases (or vice versa). An explicit checksum policy set on the session (e.g. via
     * {@code --strict-checksums}) takes precedence over either behavior, as before.
     *
     * @since 2.0.22
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_NATURE_MERGE_WEAKEST_CHECKSUM_POLICY}
     */
    public static final String CONFIG_PROP_NATURE_MERGE_WEAKEST_CHECKSUM_POLICY =
            "aether.remoteRepositoryManager.natureMergeWeakestChecksumPolicy";

    public static final boolean DEFAULT_NATURE_MERGE_WEAKEST_CHECKSUM_POLICY = false;

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
            } else if (globalPolicy
                    && !org.eclipse.aether.util.ConfigUtils.getBoolean(
                            session,
                            DEFAULT_NATURE_MERGE_WEAKEST_CHECKSUM_POLICY,
                            CONFIG_PROP_NATURE_MERGE_WEAKEST_CHECKSUM_POLICY)) {
                // merging the release and snapshot policies of a single repository into one effective policy
                // (e.g. for metadata of nature RELEASE_OR_SNAPSHOT): the result must not be weaker than what the
                // operator configured for either nature, so the stronger checksum policy wins
                checksums = strongerChecksumPolicy(policy1.getChecksumPolicy(), policy2.getChecksumPolicy());
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

    private static String strongerChecksumPolicy(String policy1, String policy2) {
        if (checksumPolicyStrength(policy2) > checksumPolicyStrength(policy1)) {
            return policy2;
        }
        return policy1;
    }

    private static int checksumPolicyStrength(String policy) {
        if (policy == null) {
            return -1;
        }
        switch (policy) {
            case RepositoryPolicy.CHECKSUM_POLICY_FAIL:
                return 2;
            case RepositoryPolicy.CHECKSUM_POLICY_WARN:
                return 1;
            case RepositoryPolicy.CHECKSUM_POLICY_IGNORE:
                return 0;
            default:
                // unknown values never win a strength comparison; they are rejected downstream when a
                // ChecksumPolicy instance is created for them
                return -1;
        }
    }
}
