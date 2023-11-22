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
package org.eclipse.aether.repository;

/**
 * A policy controlling access to a repository.
 */
public final class RepositoryPolicy {

    /**
     * Never update locally cached data.
     */
    public static final String UPDATE_POLICY_NEVER = "never";

    /**
     * Always update locally cached data.
     */
    public static final String UPDATE_POLICY_ALWAYS = "always";

    /**
     * Update locally cached data once a day.
     */
    public static final String UPDATE_POLICY_DAILY = "daily";

    /**
     * Update locally cached data every X minutes as given by "interval:X".
     */
    public static final String UPDATE_POLICY_INTERVAL = "interval";

    /**
     * Verify checksums and fail the resolution if they do not match.
     */
    public static final String CHECKSUM_POLICY_FAIL = "fail";

    /**
     * Verify checksums and warn if they do not match.
     */
    public static final String CHECKSUM_POLICY_WARN = "warn";

    /**
     * Do not verify checksums.
     */
    public static final String CHECKSUM_POLICY_IGNORE = "ignore";

    private final boolean enabled;

    private final String artifactUpdatePolicy;

    private final String metadataUpdatePolicy;

    private final String checksumPolicy;

    /**
     * Creates a new policy with checksum warnings and daily update checks.
     */
    public RepositoryPolicy() {
        this(true, UPDATE_POLICY_DAILY, UPDATE_POLICY_DAILY, CHECKSUM_POLICY_WARN);
    }

    /**
     * Creates a new policy with the specified settings (uses same update policy for data and metadata, retains old
     * resolver behaviour).
     */
    public RepositoryPolicy(boolean enabled, String updatePolicy, String checksumPolicy) {
        this(enabled, updatePolicy, updatePolicy, checksumPolicy);
    }

    /**
     * Creates a new policy with the specified settings.
     *
     * @param enabled A flag whether the associated repository should be accessed or not.
     * @param artifactUpdatePolicy The update interval after which locally cached data from the repository is considered stale
     *            and should be re-fetched, may be {@code null}.
     * @param metadataUpdatePolicy The update interval after which locally cached metadata from the repository is considered stale
     *            and should be re-fetched, may be {@code null}.
     * @param checksumPolicy The way checksum verification should be handled, may be {@code null}.
     * @since 2.0.0
     */
    public RepositoryPolicy(
            boolean enabled, String artifactUpdatePolicy, String metadataUpdatePolicy, String checksumPolicy) {
        this.enabled = enabled;
        this.artifactUpdatePolicy = (artifactUpdatePolicy != null) ? artifactUpdatePolicy : "";
        this.metadataUpdatePolicy = (metadataUpdatePolicy != null) ? metadataUpdatePolicy : "";
        this.checksumPolicy = (checksumPolicy != null) ? checksumPolicy : "";
    }

    /**
     * Indicates whether the associated repository should be contacted or not.
     *
     * @return {@code true} if the repository should be contacted, {@code false} otherwise.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * This method is not used in Resolver, as resolver internally strictly distinguishes between artifact and metadata
     * update policies.
     *
     * @see #getArtifactUpdatePolicy()
     * @see #getMetadataUpdatePolicy()
     * @deprecated This method should not be used. Since version 2 Resolver internally distinguishes between artifact
     * update policy and metadata update policy. This method was left only to preserve binary compatibility, and in
     * reality invokes {@link #getArtifactUpdatePolicy()}.
     */
    @Deprecated
    public String getUpdatePolicy() {
        return getArtifactUpdatePolicy();
    }

    /**
     * Gets the update policy for locally cached artifacts from the repository.
     *
     * @return The update policy, never {@code null}.
     * @since 2.0.0
     */
    public String getArtifactUpdatePolicy() {
        return artifactUpdatePolicy;
    }

    /**
     * Gets the update policy for locally cached metadata from the repository.
     *
     * @return The update policy, never {@code null}.
     * @since 2.0.0
     */
    public String getMetadataUpdatePolicy() {
        return metadataUpdatePolicy;
    }

    /**
     * Gets the policy for checksum validation.
     *
     * @return The checksum policy, never {@code null}.
     */
    public String getChecksumPolicy() {
        return checksumPolicy;
    }

    @Override
    public String toString() {
        return "enabled=" + isEnabled()
                + ", checksums=" + getChecksumPolicy()
                + ", artifactUpdates=" + getArtifactUpdatePolicy()
                + ", metadataUpdates=" + getMetadataUpdatePolicy();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || !getClass().equals(obj.getClass())) {
            return false;
        }

        RepositoryPolicy that = (RepositoryPolicy) obj;

        return enabled == that.enabled
                && artifactUpdatePolicy.equals(that.artifactUpdatePolicy)
                && metadataUpdatePolicy.equals(that.metadataUpdatePolicy)
                && checksumPolicy.equals(that.checksumPolicy);
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + (enabled ? 1 : 0);
        hash = hash * 31 + artifactUpdatePolicy.hashCode();
        hash = hash * 31 + metadataUpdatePolicy.hashCode();
        hash = hash * 31 + checksumPolicy.hashCode();
        return hash;
    }
}
