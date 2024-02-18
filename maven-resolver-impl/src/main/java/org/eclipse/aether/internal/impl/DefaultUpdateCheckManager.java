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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SessionData;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.UpdateCheck;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.impl.UpdatePolicyAnalyzer;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.AuthenticationDigest;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 */
@Singleton
@Named
public class DefaultUpdateCheckManager implements UpdateCheckManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUpdatePolicyAnalyzer.class);

    private static final String UPDATED_KEY_SUFFIX = ".lastUpdated";

    private static final String ERROR_KEY_SUFFIX = ".error";

    private static final String NOT_FOUND = "";

    static final Object SESSION_CHECKS = new Object() {
        @Override
        public String toString() {
            return "updateCheckManager.checks";
        }
    };

    /**
     * Manages the session state, i.e. influences if the same download requests to artifacts/metadata will happen
     * multiple times within the same RepositorySystemSession. If "enabled" will enable the session state. If "bypass"
     * will enable bypassing (i.e. store all artifact ids/metadata ids which have been updates but not evaluating
     * those). All other values lead to disabling the session state completely.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_SESSION_STATE}
     */
    public static final String CONFIG_PROP_SESSION_STATE =
            ConfigurationProperties.PREFIX_AETHER + "updateCheckManager.sessionState";

    public static final String DEFAULT_SESSION_STATE = "enabled";

    private static final int STATE_ENABLED = 0;

    private static final int STATE_BYPASS = 1;

    private static final int STATE_DISABLED = 2;

    /**
     * This "last modified" timestamp is used when no local file is present, signaling "first attempt" to cache a file,
     * but as it is not present, outcome is simply always "go get it".
     * <p>
     * Its meaning is "we never downloaded it", so go grab it.
     */
    private static final long TS_NEVER = 0L;

    /**
     * This "last modified" timestamp is returned by {@link #getLastUpdated(Properties, String)} method when the
     * timestamp entry is not found (due properties file not present or key not present in properties file, irrelevant).
     * It means that the cached file (artifact or metadata) is present, but we cannot tell when was it downloaded. In
     * this case, it is {@link UpdatePolicyAnalyzer} applying in-effect policy, that decide is update (re-download)
     * needed or not. For example, if policy is "never", we should not re-download the file.
     * <p>
     * Its meaning is "we downloaded it, but have no idea when", so let the policy decide its fate.
     */
    private static final long TS_UNKNOWN = 1L;

    private final TrackingFileManager trackingFileManager;

    private final UpdatePolicyAnalyzer updatePolicyAnalyzer;

    private final PathProcessor pathProcessor;

    @Inject
    public DefaultUpdateCheckManager(
            TrackingFileManager trackingFileManager,
            UpdatePolicyAnalyzer updatePolicyAnalyzer,
            PathProcessor pathProcessor) {
        this.trackingFileManager = requireNonNull(trackingFileManager, "tracking file manager cannot be null");
        this.updatePolicyAnalyzer = requireNonNull(updatePolicyAnalyzer, "update policy analyzer cannot be null");
        this.pathProcessor = requireNonNull(pathProcessor, "path processor cannot be null");
    }

    @Override
    public void checkArtifact(RepositorySystemSession session, UpdateCheck<Artifact, ArtifactTransferException> check) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(check, "check cannot be null");
        final String updatePolicy = check.getArtifactPolicy();
        if (check.getLocalLastUpdated() != 0
                && !isUpdatedRequired(session, check.getLocalLastUpdated(), updatePolicy)) {
            LOGGER.debug("Skipped remote request for {}, locally installed artifact up-to-date", check.getItem());

            check.setRequired(false);
            return;
        }

        Artifact artifact = check.getItem();
        RemoteRepository repository = check.getRepository();

        Path artifactPath =
                requireNonNull(check.getPath(), String.format("The artifact '%s' has no file attached", artifact));

        boolean fileExists = check.isFileValid() && Files.exists(artifactPath);

        Path touchPath = getArtifactTouchFile(artifactPath);
        Properties props = read(touchPath);

        String updateKey = getUpdateKey(session, artifactPath, repository);
        String dataKey = getDataKey(repository);

        String error = getError(props, dataKey);

        long lastUpdated;
        if (error == null) {
            if (fileExists) {
                // last update was successful
                lastUpdated = pathProcessor.lastModified(artifactPath, 0L);
            } else {
                // this is the first attempt ever
                lastUpdated = TS_NEVER;
            }
        } else if (error.isEmpty()) {
            // artifact did not exist
            lastUpdated = getLastUpdated(props, dataKey);
        } else {
            // artifact could not be transferred
            String transferKey = getTransferKey(session, repository);
            lastUpdated = getLastUpdated(props, transferKey);
        }

        if (lastUpdated == TS_NEVER) {
            check.setRequired(true);
        } else if (isAlreadyUpdated(session, updateKey)) {
            LOGGER.debug("Skipped remote request for {}, already updated during this session", check.getItem());

            check.setRequired(false);
            if (error != null) {
                check.setException(newException(error, artifact, repository));
            }
        } else if (isUpdatedRequired(session, lastUpdated, updatePolicy)) {
            check.setRequired(true);
        } else if (fileExists) {
            LOGGER.debug("Skipped remote request for {}, locally cached artifact up-to-date", check.getItem());

            check.setRequired(false);
        } else {
            int errorPolicy = Utils.getPolicy(session, artifact, repository);
            int cacheFlag = getCacheFlag(error);
            if ((errorPolicy & cacheFlag) != 0) {
                check.setRequired(false);
                check.setException(newException(error, artifact, repository));
            } else {
                check.setRequired(true);
            }
        }
    }

    private static int getCacheFlag(String error) {
        if (error == null || error.isEmpty()) {
            return ResolutionErrorPolicy.CACHE_NOT_FOUND;
        } else {
            return ResolutionErrorPolicy.CACHE_TRANSFER_ERROR;
        }
    }

    private ArtifactTransferException newException(String error, Artifact artifact, RemoteRepository repository) {
        if (error == null || error.isEmpty()) {
            return new ArtifactNotFoundException(
                    artifact,
                    repository,
                    artifact
                            + " was not found in " + repository.getUrl()
                            + " during a previous attempt. This failure was"
                            + " cached in the local repository and"
                            + " resolution is not reattempted until the update interval of " + repository.getId()
                            + " has elapsed or updates are forced",
                    true);
        } else {
            return new ArtifactTransferException(
                    artifact,
                    repository,
                    artifact + " failed to transfer from "
                            + repository.getUrl() + " during a previous attempt. This failure"
                            + " was cached in the local repository and"
                            + " resolution is not reattempted until the update interval of " + repository.getId()
                            + " has elapsed or updates are forced. Original error: " + error,
                    true);
        }
    }

    @Override
    public void checkMetadata(RepositorySystemSession session, UpdateCheck<Metadata, MetadataTransferException> check) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(check, "check cannot be null");
        final String updatePolicy = check.getMetadataPolicy();
        if (check.getLocalLastUpdated() != 0
                && !isUpdatedRequired(session, check.getLocalLastUpdated(), updatePolicy)) {
            LOGGER.debug("Skipped remote request for {} locally installed metadata up-to-date", check.getItem());

            check.setRequired(false);
            return;
        }

        Metadata metadata = check.getItem();
        RemoteRepository repository = check.getRepository();

        Path metadataPath =
                requireNonNull(check.getPath(), String.format("The metadata '%s' has no file attached", metadata));

        boolean fileExists = check.isFileValid() && Files.exists(metadataPath);

        Path touchPath = getMetadataTouchFile(metadataPath);
        Properties props = read(touchPath);

        String updateKey = getUpdateKey(session, metadataPath, repository);
        String dataKey = getDataKey(metadataPath);

        String error = getError(props, dataKey);

        long lastUpdated;
        if (error == null) {
            if (fileExists) {
                // last update was successful
                lastUpdated = getLastUpdated(props, dataKey);
            } else {
                // this is the first attempt ever
                lastUpdated = TS_NEVER;
            }
        } else if (error.isEmpty()) {
            // metadata did not exist
            lastUpdated = getLastUpdated(props, dataKey);
        } else {
            // metadata could not be transferred
            String transferKey = getTransferKey(session, metadataPath, repository);
            lastUpdated = getLastUpdated(props, transferKey);
        }

        if (lastUpdated == TS_NEVER) {
            check.setRequired(true);
        } else if (isAlreadyUpdated(session, updateKey)) {
            LOGGER.debug("Skipped remote request for {}, already updated during this session", check.getItem());

            check.setRequired(false);
            if (error != null) {
                check.setException(newException(error, metadata, repository));
            }
        } else if (isUpdatedRequired(session, lastUpdated, updatePolicy)) {
            check.setRequired(true);
        } else if (fileExists) {
            LOGGER.debug("Skipped remote request for {}, locally cached metadata up-to-date", check.getItem());

            check.setRequired(false);
        } else {
            int errorPolicy = Utils.getPolicy(session, metadata, repository);
            int cacheFlag = getCacheFlag(error);
            if ((errorPolicy & cacheFlag) != 0) {
                check.setRequired(false);
                check.setException(newException(error, metadata, repository));
            } else {
                check.setRequired(true);
            }
        }
    }

    private MetadataTransferException newException(String error, Metadata metadata, RemoteRepository repository) {
        if (error == null || error.isEmpty()) {
            return new MetadataNotFoundException(
                    metadata,
                    repository,
                    metadata + " was not found in "
                            + repository.getUrl() + " during a previous attempt."
                            + " This failure was cached in the local repository and"
                            + " resolution is not be reattempted until the update interval of " + repository.getId()
                            + " has elapsed or updates are forced",
                    true);
        } else {
            return new MetadataTransferException(
                    metadata,
                    repository,
                    metadata + " failed to transfer from "
                            + repository.getUrl() + " during a previous attempt."
                            + " This failure was cached in the local repository and"
                            + " resolution will not be reattempted until the update interval of " + repository.getId()
                            + " has elapsed or updates are forced. Original error: " + error,
                    true);
        }
    }

    private long getLastUpdated(Properties props, String key) {
        String value = props.getProperty(key + UPDATED_KEY_SUFFIX, "");
        try {
            return (!value.isEmpty()) ? Long.parseLong(value) : TS_UNKNOWN;
        } catch (NumberFormatException e) {
            LOGGER.debug("Cannot parse last updated date {}, ignoring it", value, e);
            return TS_UNKNOWN;
        }
    }

    private String getError(Properties props, String key) {
        return props.getProperty(key + ERROR_KEY_SUFFIX);
    }

    private Path getArtifactTouchFile(Path artifactPath) {
        return artifactPath.getParent().resolve(artifactPath.getFileName() + UPDATED_KEY_SUFFIX);
    }

    private Path getMetadataTouchFile(Path metadataPath) {
        return metadataPath.getParent().resolve("resolver-status.properties");
    }

    private String getDataKey(RemoteRepository repository) {
        Set<String> mirroredUrls = Collections.emptySet();
        if (repository.isRepositoryManager()) {
            mirroredUrls = new TreeSet<>();
            for (RemoteRepository mirroredRepository : repository.getMirroredRepositories()) {
                mirroredUrls.add(normalizeRepoUrl(mirroredRepository.getUrl()));
            }
        }

        StringBuilder buffer = new StringBuilder(1024);

        buffer.append(normalizeRepoUrl(repository.getUrl()));
        for (String mirroredUrl : mirroredUrls) {
            buffer.append('+').append(mirroredUrl);
        }

        return buffer.toString();
    }

    private String getTransferKey(RepositorySystemSession session, RemoteRepository repository) {
        return getRepoKey(session, repository);
    }

    private String getDataKey(Path metadataPath) {
        return metadataPath.getFileName().toString();
    }

    private String getTransferKey(RepositorySystemSession session, Path metadataPath, RemoteRepository repository) {
        return metadataPath.getFileName().toString() + '/' + getRepoKey(session, repository);
    }

    private String getRepoKey(RepositorySystemSession session, RemoteRepository repository) {
        StringBuilder buffer = new StringBuilder(128);

        Proxy proxy = repository.getProxy();
        if (proxy != null) {
            buffer.append(AuthenticationDigest.forProxy(session, repository)).append('@');
            buffer.append(proxy.getHost()).append(':').append(proxy.getPort()).append('>');
        }

        buffer.append(AuthenticationDigest.forRepository(session, repository)).append('@');

        buffer.append(repository.getContentType()).append('-');
        buffer.append(repository.getId()).append('-');
        buffer.append(normalizeRepoUrl(repository.getUrl()));

        return buffer.toString();
    }

    private String normalizeRepoUrl(String url) {
        String result = url;
        if (url != null && !url.isEmpty() && !url.endsWith("/")) {
            result = url + '/';
        }
        return result;
    }

    private String getUpdateKey(RepositorySystemSession session, Path path, RemoteRepository repository) {
        return path.toAbsolutePath() + "|" + getRepoKey(session, repository);
    }

    private int getSessionState(RepositorySystemSession session) {
        String mode = ConfigUtils.getString(session, DEFAULT_SESSION_STATE, CONFIG_PROP_SESSION_STATE);
        if (Boolean.parseBoolean(mode) || "enabled".equalsIgnoreCase(mode)) {
            // perform update check at most once per session, regardless of update policy
            return STATE_ENABLED;
        } else if ("bypass".equalsIgnoreCase(mode)) {
            // evaluate update policy but record update in session to prevent potential future checks
            return STATE_BYPASS;
        } else {
            // no session state at all, always evaluate update policy
            return STATE_DISABLED;
        }
    }

    private boolean isAlreadyUpdated(RepositorySystemSession session, Object updateKey) {
        if (getSessionState(session) >= STATE_BYPASS) {
            return false;
        }
        SessionData data = session.getData();
        Object checkedFiles = data.get(SESSION_CHECKS);
        if (!(checkedFiles instanceof Map)) {
            return false;
        }
        return ((Map<?, ?>) checkedFiles).containsKey(updateKey);
    }

    @SuppressWarnings("unchecked")
    private void setUpdated(RepositorySystemSession session, Object updateKey) {
        if (getSessionState(session) >= STATE_DISABLED) {
            return;
        }
        SessionData data = session.getData();
        Object checkedFiles = data.computeIfAbsent(SESSION_CHECKS, () -> new ConcurrentHashMap<>(256));
        ((Map<Object, Boolean>) checkedFiles).put(updateKey, Boolean.TRUE);
    }

    private boolean isUpdatedRequired(RepositorySystemSession session, long lastModified, String policy) {
        return updatePolicyAnalyzer.isUpdatedRequired(session, lastModified, policy);
    }

    private Properties read(Path touchPath) {
        Properties props = trackingFileManager.read(touchPath);
        return (props != null) ? props : new Properties();
    }

    @Override
    public void touchArtifact(RepositorySystemSession session, UpdateCheck<Artifact, ArtifactTransferException> check) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(check, "check cannot be null");
        Path artifactPath = check.getPath();
        Path touchPath = getArtifactTouchFile(artifactPath);

        String updateKey = getUpdateKey(session, artifactPath, check.getRepository());
        String dataKey = getDataKey(check.getAuthoritativeRepository());
        String transferKey = getTransferKey(session, check.getRepository());

        setUpdated(session, updateKey);
        Properties props = write(touchPath, dataKey, transferKey, check.getException());

        if (Files.exists(artifactPath) && !hasErrors(props)) {
            try {
                Files.deleteIfExists(touchPath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private boolean hasErrors(Properties props) {
        for (Object key : props.keySet()) {
            if (key.toString().endsWith(ERROR_KEY_SUFFIX)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void touchMetadata(RepositorySystemSession session, UpdateCheck<Metadata, MetadataTransferException> check) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(check, "check cannot be null");
        Path metadataPath = check.getPath();
        Path touchPath = getMetadataTouchFile(metadataPath);

        String updateKey = getUpdateKey(session, metadataPath, check.getRepository());
        String dataKey = getDataKey(metadataPath);
        String transferKey = getTransferKey(session, metadataPath, check.getRepository());

        setUpdated(session, updateKey);
        write(touchPath, dataKey, transferKey, check.getException());
    }

    private Properties write(Path touchPath, String dataKey, String transferKey, Exception error) {
        Map<String, String> updates = new HashMap<>();

        String timestamp = Long.toString(System.currentTimeMillis());

        if (error == null) {
            updates.put(dataKey + ERROR_KEY_SUFFIX, null);
            updates.put(dataKey + UPDATED_KEY_SUFFIX, timestamp);
            updates.put(transferKey + UPDATED_KEY_SUFFIX, null);
        } else if (error instanceof ArtifactNotFoundException || error instanceof MetadataNotFoundException) {
            updates.put(dataKey + ERROR_KEY_SUFFIX, NOT_FOUND);
            updates.put(dataKey + UPDATED_KEY_SUFFIX, timestamp);
            updates.put(transferKey + UPDATED_KEY_SUFFIX, null);
        } else {
            String msg = error.getMessage();
            if (msg == null || msg.isEmpty()) {
                msg = error.getClass().getSimpleName();
            }
            updates.put(dataKey + ERROR_KEY_SUFFIX, msg);
            updates.put(dataKey + UPDATED_KEY_SUFFIX, null);
            updates.put(transferKey + UPDATED_KEY_SUFFIX, timestamp);
        }

        return trackingFileManager.update(touchPath, updates);
    }
}
