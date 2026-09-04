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

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

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
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
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
public class DefaultUpdateCheckManager implements UpdateCheckManager, Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUpdatePolicyAnalyzer.class);

    private TrackingFileManager trackingFileManager;

    private UpdatePolicyAnalyzer updatePolicyAnalyzer;

    private static final String UPDATED_KEY_SUFFIX = ".lastUpdated";

    private static final String ERROR_KEY_SUFFIX = ".error";

    private static final String NOT_FOUND = "";

    static final Object SESSION_CHECKS = new Object() {
        @Override
        public String toString() {
            return "updateCheckManager.checks";
        }
    };

    static final Object SESSION_NOT_FOUNDS = new Object() {
        @Override
        public String toString() {
            return "updateCheckManager.notFounds";
        }
    };

    static final String CONFIG_PROP_SESSION_STATE = "aether.updateCheckManager.sessionState";

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

    @Deprecated
    public DefaultUpdateCheckManager() {
        // default ctor for ServiceLocator
    }

    @Inject
    public DefaultUpdateCheckManager(
            TrackingFileManager trackingFileManager, UpdatePolicyAnalyzer updatePolicyAnalyzer) {
        setTrackingFileManager(trackingFileManager);
        setUpdatePolicyAnalyzer(updatePolicyAnalyzer);
    }

    public void initService(ServiceLocator locator) {
        setTrackingFileManager(locator.getService(TrackingFileManager.class));
        setUpdatePolicyAnalyzer(locator.getService(UpdatePolicyAnalyzer.class));
    }

    public DefaultUpdateCheckManager setTrackingFileManager(TrackingFileManager trackingFileManager) {
        this.trackingFileManager = requireNonNull(trackingFileManager);
        return this;
    }

    public DefaultUpdateCheckManager setUpdatePolicyAnalyzer(UpdatePolicyAnalyzer updatePolicyAnalyzer) {
        this.updatePolicyAnalyzer = requireNonNull(updatePolicyAnalyzer, "update policy analyzer cannot be null");
        return this;
    }

    public void checkArtifact(RepositorySystemSession session, UpdateCheck<Artifact, ArtifactTransferException> check) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(check, "check cannot be null");
        if (check.getLocalLastUpdated() != 0
                && !isUpdatedRequired(session, check.getLocalLastUpdated(), check.getPolicy())) {
            LOGGER.debug("Skipped remote request for {}, locally installed artifact up-to-date", check.getItem());

            check.setRequired(false);
            return;
        }

        Artifact artifact = check.getItem();
        RemoteRepository repository = check.getRepository();

        File artifactFile =
                requireNonNull(check.getFile(), String.format("The artifact '%s' has no file attached", artifact));

        boolean fileExists = check.isFileValid() && artifactFile.exists();

        File touchFile = getArtifactTouchFile(artifactFile);
        Properties props = read(touchFile);

        String updateKey = getUpdateKey(session, artifactFile, repository);
        String dataKey = getDataKey(repository);

        String error = getError(props, dataKey);

        long lastUpdated;
        if (error == null) {
            if (fileExists) {
                // last update was successful
                lastUpdated = artifactFile.lastModified();
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
        } else if (isUpdatedRequired(session, lastUpdated, check.getPolicy())) {
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

    public void checkMetadata(RepositorySystemSession session, UpdateCheck<Metadata, MetadataTransferException> check) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(check, "check cannot be null");
        if (check.getLocalLastUpdated() != 0
                && !isUpdatedRequired(session, check.getLocalLastUpdated(), check.getPolicy())) {
            LOGGER.debug("Skipped remote request for {} locally installed metadata up-to-date", check.getItem());

            check.setRequired(false);
            return;
        }

        Metadata metadata = check.getItem();
        RemoteRepository repository = check.getRepository();

        File metadataFile =
                requireNonNull(check.getFile(), String.format("The metadata '%s' has no file attached", metadata));

        boolean fileExists = check.isFileValid() && metadataFile.exists();

        File touchFile = getMetadataTouchFile(metadataFile);
        Properties props = read(touchFile);

        String updateKey = getUpdateKey(session, metadataFile, repository);
        String dataKey = getDataKey(metadataFile);

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
            String transferKey = getTransferKey(session, metadataFile, repository);
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
        } else if (isUpdatedRequired(session, lastUpdated, check.getPolicy())) {
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
            return (value.length() > 0) ? Long.parseLong(value) : TS_UNKNOWN;
        } catch (NumberFormatException e) {
            LOGGER.debug("Cannot parse last updated date {}, ignoring it", value, e);
            return TS_UNKNOWN;
        }
    }

    private String getError(Properties props, String key) {
        return props.getProperty(key + ERROR_KEY_SUFFIX);
    }

    private File getArtifactTouchFile(File artifactFile) {
        return new File(artifactFile.getPath() + UPDATED_KEY_SUFFIX);
    }

    private File getMetadataTouchFile(File metadataFile) {
        return new File(metadataFile.getParent(), "resolver-status.properties");
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

    private String getDataKey(File metadataFile) {
        return metadataFile.getName();
    }

    private String getTransferKey(RepositorySystemSession session, File metadataFile, RemoteRepository repository) {
        return metadataFile.getName() + '/' + getRepoKey(session, repository);
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
        if (url != null && url.length() > 0 && !url.endsWith("/")) {
            result = url + '/';
        }
        return result;
    }

    private String getUpdateKey(RepositorySystemSession session, File file, RemoteRepository repository) {
        return file.getAbsolutePath() + '|' + getRepoKey(session, repository);
    }

    private int getSessionState(RepositorySystemSession session) {
        String mode = ConfigUtils.getString(session, "enabled", CONFIG_PROP_SESSION_STATE);
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

    private String getNotFoundKey(File touchFile, String dataKey) {
        return touchFile.getAbsolutePath() + "|" + dataKey;
    }

    /**
     * Records that a not-found marker has been written during this session, so that a later success from a sibling
     * repository can tell the ordinary fall-through between repositories apart from a stale marker left by a
     * previous session. Unlike {@link #setUpdated(RepositorySystemSession, String)} this is not subject to
     * {@link #CONFIG_PROP_SESSION_STATE}, as it only influences logging.
     */
    @SuppressWarnings("unchecked")
    private void setNotFound(RepositorySystemSession session, String notFoundKey) {
        Object notFounds = session.getData().computeIfAbsent(SESSION_NOT_FOUNDS, () -> new ConcurrentHashMap<>(64));
        ((Map<String, Boolean>) notFounds).put(notFoundKey, Boolean.TRUE);
    }

    private boolean isNotFoundInSession(RepositorySystemSession session, String notFoundKey) {
        Object notFounds = session.getData().get(SESSION_NOT_FOUNDS);
        return notFounds instanceof Map && ((Map<?, ?>) notFounds).containsKey(notFoundKey);
    }

    private Properties read(File touchFile) {
        Properties props = trackingFileManager.read(touchFile);
        return (props != null) ? props : new Properties();
    }

    public void touchArtifact(RepositorySystemSession session, UpdateCheck<Artifact, ArtifactTransferException> check) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(check, "check cannot be null");
        File artifactFile = check.getFile();
        File touchFile = getArtifactTouchFile(artifactFile);

        String updateKey = getUpdateKey(session, artifactFile, check.getRepository());
        String dataKey = getDataKey(check.getAuthoritativeRepository());
        String transferKey = getTransferKey(session, check.getRepository());

        setUpdated(session, updateKey);
        Map<String, String> staleSiblingNotFounds = Collections.emptyMap();
        if (check.getException() == null) {
            staleSiblingNotFounds = getStaleSiblingNotFounds(
                    session, read(touchFile), touchFile, dataKey, check.getItem(), check.getRepository());
        } else if (check.getException() instanceof ArtifactNotFoundException) {
            setNotFound(session, getNotFoundKey(touchFile, dataKey));
        }
        Properties props = write(touchFile, dataKey, transferKey, check.getException(), staleSiblingNotFounds);

        if (artifactFile.exists() && !hasErrors(props)) {
            trackingFileManager.delete(touchFile);
        }
    }

    /**
     * Collects, for removal, cached not-found markers left by other repositories for an artifact that has just
     * been successfully downloaded. A cached not-found silently suppresses any remote contact with the repository
     * that issued it, so a single spoofed or transient "not found" answer would otherwise durably reroute
     * resolution of the artifact to whatever lower-prioritized repository serves it. Expiring the marker forces a
     * confirming re-check of the suppressed repository (subject to the update policy) the next time it is
     * consulted, and the reroute is surfaced at WARN level instead of happening silently.
     * <p>
     * A marker written earlier in the same session is a different situation: repositories are consulted in
     * order, so the first repository answering not-found and a later one serving the artifact is the ordinary
     * fall-through, not a suppressed repository. Such markers are expired all the same, but only logged at DEBUG.
     */
    private Map<String, String> getStaleSiblingNotFounds(
            RepositorySystemSession session,
            Properties props,
            File touchFile,
            String dataKey,
            Artifact artifact,
            RemoteRepository repository) {
        Map<String, String> removals = null;
        for (Object k : props.keySet()) {
            String key = k.toString();
            if (key.endsWith(ERROR_KEY_SUFFIX)) {
                String otherDataKey = key.substring(0, key.length() - ERROR_KEY_SUFFIX.length());
                if (!otherDataKey.equals(dataKey) && NOT_FOUND.equals(props.getProperty(key))) {
                    if (isNotFoundInSession(session, getNotFoundKey(touchFile, otherDataKey))) {
                        LOGGER.debug(
                                "{} was downloaded from {} after {} answered not-found earlier in this session;"
                                        + " not caching that not-found",
                                artifact,
                                repository.getUrl(),
                                otherDataKey);
                    } else {
                        LOGGER.warn(
                                "{} was downloaded from {} while a not-found cached by a previous session suppresses"
                                        + " re-checking {}; expiring the cached not-found so that repository is"
                                        + " checked again on the next resolution",
                                artifact,
                                repository.getUrl(),
                                otherDataKey);
                    }
                    if (removals == null) {
                        removals = new HashMap<>();
                    }
                    removals.put(key, null);
                    removals.put(otherDataKey + UPDATED_KEY_SUFFIX, null);
                }
            }
        }
        return removals != null ? removals : Collections.emptyMap();
    }

    private boolean hasErrors(Properties props) {
        for (Object key : props.keySet()) {
            if (key.toString().endsWith(ERROR_KEY_SUFFIX)) {
                return true;
            }
        }
        return false;
    }

    public void touchMetadata(RepositorySystemSession session, UpdateCheck<Metadata, MetadataTransferException> check) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(check, "check cannot be null");
        File metadataFile = check.getFile();
        File touchFile = getMetadataTouchFile(metadataFile);

        String updateKey = getUpdateKey(session, metadataFile, check.getRepository());
        String dataKey = getDataKey(metadataFile);
        String transferKey = getTransferKey(session, metadataFile, check.getRepository());

        setUpdated(session, updateKey);
        write(touchFile, dataKey, transferKey, check.getException(), Collections.emptyMap());
    }

    private Properties write(
            File touchFile, String dataKey, String transferKey, Exception error, Map<String, String> extraUpdates) {
        Map<String, String> updates = new HashMap<>(extraUpdates);

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

        return trackingFileManager.update(touchFile, updates);
    }
}
