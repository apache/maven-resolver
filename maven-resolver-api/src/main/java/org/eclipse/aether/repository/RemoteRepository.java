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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * A repository on a remote server.
 * <p>
 * If use of instances of this class are meant to be used as keys, see {@link #toBareRemoteRepository()} method.
 */
public final class RemoteRepository implements ArtifactRepository {
    /**
     * The intent this repository is to be used for. Newly created repositories are usually "bare", and before
     * their actual use (caller or repository system) adapts them, equip with auth/proxy info and even mirrors, if
     * environment is configured for them. Note: "bare" does not always mean "without authentication", as client
     * code may create with all required properties, but {@link org.eclipse.aether.RepositorySystem} will process
     * them anyway, marking their "intent".
     * <p>
     * <em>Important consequence:</em> the change of {@link Intent} on repository may affect the use cases when
     * they are used as keys (they are suitable for that). To use {@link RemoteRepository} instances as key,
     * you should use instances returned by method {@link #toBareRemoteRepository()}, that returns "normalized"
     * repository instances usable as keys. Also, in "key usage case" two instances of remote repository are
     * considered equal if following stands: {@code Objects.equals(r1.toBareRemoteRepository(), r2.toBareRemoteRepository())}.
     *
     * @see org.eclipse.aether.RepositorySystem#newResolutionRepositories(RepositorySystemSession, List)
     * @see org.eclipse.aether.RepositorySystem#newDeploymentRepository(RepositorySystemSession, RemoteRepository)
     * @since 2.0.14
     */
    public enum Intent {
        BARE,
        RESOLUTION,
        DEPLOYMENT
    }

    private static final Pattern URL_PATTERN =
            Pattern.compile("([^:/]+(:[^:/]{2,}+(?=://))?):(//([^@/]*@)?([^/:]+))?.*");

    private final String id;

    private final String type;

    private final String url;

    private final String host;

    private final String protocol;

    private final RepositoryPolicy releasePolicy;

    private final RepositoryPolicy snapshotPolicy;

    private final Proxy proxy;

    private final Authentication authentication;

    private final List<RemoteRepository> mirroredRepositories;

    private final boolean repositoryManager;

    private final boolean blocked;

    private final Intent intent;

    private final int hashCode;

    RemoteRepository(Builder builder) {
        if (builder.prototype != null) {
            id = (builder.delta & Builder.ID) != 0 ? builder.id : builder.prototype.id;
            type = (builder.delta & Builder.TYPE) != 0 ? builder.type : builder.prototype.type;
            url = (builder.delta & Builder.URL) != 0 ? builder.url : builder.prototype.url;
            releasePolicy =
                    (builder.delta & Builder.RELEASES) != 0 ? builder.releasePolicy : builder.prototype.releasePolicy;
            snapshotPolicy = (builder.delta & Builder.SNAPSHOTS) != 0
                    ? builder.snapshotPolicy
                    : builder.prototype.snapshotPolicy;
            proxy = (builder.delta & Builder.PROXY) != 0 ? builder.proxy : builder.prototype.proxy;
            authentication =
                    (builder.delta & Builder.AUTH) != 0 ? builder.authentication : builder.prototype.authentication;
            repositoryManager = (builder.delta & Builder.REPOMAN) != 0
                    ? builder.repositoryManager
                    : builder.prototype.repositoryManager;
            blocked = (builder.delta & Builder.BLOCKED) != 0 ? builder.blocked : builder.prototype.blocked;
            mirroredRepositories = (builder.delta & Builder.MIRRORED) != 0
                    ? copy(builder.mirroredRepositories)
                    : builder.prototype.mirroredRepositories;
            intent = (builder.delta & Builder.INTENT) != 0 ? builder.intent : builder.prototype.intent;
        } else {
            id = builder.id;
            type = builder.type;
            url = builder.url;
            releasePolicy = builder.releasePolicy;
            snapshotPolicy = builder.snapshotPolicy;
            proxy = builder.proxy;
            authentication = builder.authentication;
            repositoryManager = builder.repositoryManager;
            blocked = builder.blocked;
            mirroredRepositories = copy(builder.mirroredRepositories);
            intent = builder.intent;
        }

        Matcher m = URL_PATTERN.matcher(url);
        if (m.matches()) {
            String h = m.group(5);
            this.host = (h != null) ? h : "";
            this.protocol = m.group(1);
        } else {
            this.host = "";
            this.protocol = "";
        }

        this.hashCode = Objects.hash(
                id,
                type,
                url, // host, protocol derived from url
                releasePolicy,
                snapshotPolicy,
                proxy,
                authentication,
                mirroredRepositories,
                repositoryManager,
                blocked,
                intent);
    }

    private static List<RemoteRepository> copy(List<RemoteRepository> repos) {
        if (repos == null || repos.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(Arrays.asList(repos.toArray(new RemoteRepository[0])));
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getContentType() {
        return type;
    }

    /**
     * Gets the (base) URL of this repository.
     *
     * @return The (base) URL of this repository, never {@code null}.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Gets the protocol part from the repository's URL, for example {@code file} or {@code http}. As suggested by RFC
     * 2396, section 3.1 "Scheme Component", the protocol name should be treated case-insensitively.
     *
     * @return The protocol or an empty string if none, never {@code null}.
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Gets the host part from the repository's URL.
     *
     * @return The host or an empty string if none, never {@code null}.
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the policy to apply for snapshot/release artifacts.
     *
     * @param snapshot {@code true} to retrieve the snapshot policy, {@code false} to retrieve the release policy.
     * @return The requested repository policy, never {@code null}.
     */
    public RepositoryPolicy getPolicy(boolean snapshot) {
        return snapshot ? snapshotPolicy : releasePolicy;
    }

    /**
     * Gets the proxy that has been selected for this repository.
     *
     * @return The selected proxy or {@code null} if none.
     */
    public Proxy getProxy() {
        return proxy;
    }

    /**
     * Gets the authentication that has been selected for this repository.
     *
     * @return The selected authentication or {@code null} if none.
     */
    public Authentication getAuthentication() {
        return authentication;
    }

    /**
     * Gets the repositories that this repository serves as a mirror for.
     *
     * @return The (read-only) repositories being mirrored by this repository, never {@code null}.
     */
    public List<RemoteRepository> getMirroredRepositories() {
        return mirroredRepositories;
    }

    /**
     * Indicates whether this repository refers to a repository manager or not.
     *
     * @return {@code true} if this repository is a repository manager, {@code false} otherwise.
     */
    public boolean isRepositoryManager() {
        return repositoryManager;
    }

    /**
     * Indicates whether this repository is blocked from performing any download requests.
     *
     * @return {@code true} if this repository is blocked from performing any download requests,
     *         {@code false} otherwise.
     */
    public boolean isBlocked() {
        return blocked;
    }

    /**
     * Returns the intent this repository is prepared for.
     *
     * @return The intent this repository is prepared for.
     * @since 2.0.14
     */
    public Intent getIntent() {
        return intent;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder(256);
        buffer.append(getId());
        buffer.append(" (").append(getUrl());
        buffer.append(", ").append(getContentType());
        boolean r = getPolicy(false).isEnabled(), s = getPolicy(true).isEnabled();
        if (r && s) {
            buffer.append(", releases+snapshots");
        } else if (r) {
            buffer.append(", releases");
        } else if (s) {
            buffer.append(", snapshots");
        } else {
            buffer.append(", disabled");
        }
        if (isRepositoryManager()) {
            buffer.append(", managed");
        }
        if (isBlocked()) {
            buffer.append(", blocked");
        }
        buffer.append(", ").append(getIntent().name()).append(")");
        buffer.append(")");
        return buffer.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !getClass().equals(obj.getClass())) {
            return false;
        }

        RemoteRepository that = (RemoteRepository) obj;

        return Objects.equals(url, that.url)
                && Objects.equals(type, that.type)
                && Objects.equals(id, that.id)
                && Objects.equals(releasePolicy, that.releasePolicy)
                && Objects.equals(snapshotPolicy, that.snapshotPolicy)
                && Objects.equals(proxy, that.proxy)
                && Objects.equals(authentication, that.authentication)
                && Objects.equals(mirroredRepositories, that.mirroredRepositories)
                && repositoryManager == that.repositoryManager
                && blocked == that.blocked
                && intent == that.intent;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * Makes "bare" repository out of this instance, usable as keys within one single session, by applying following
     * changes to repository (returns new instance):
     * <ul>
     *     <li>sets intent to {@link Intent#BARE}</li>
     *     <li>nullifies proxy</li>
     *     <li>nullifies authentication</li>
     *     <li>nullifies mirrors</li>
     *     <li>sets repositoryManager to {@code false}</li>
     * </ul>
     * These properties are managed by repository system, based on configuration. See {@link org.eclipse.aether.RepositorySystem}
     * and (internal component) {@code org.eclipse.aether.impl.RemoteRepositoryManager}.
     */
    public RemoteRepository toBareRemoteRepository() {
        return new Builder(this)
                .setIntent(Intent.BARE)
                .setProxy(null)
                .setAuthentication(null)
                .setMirroredRepositories(null)
                .setRepositoryManager(false)
                .build();
    }

    /**
     * A builder to create remote repositories.
     */
    public static final class Builder {

        private static final RepositoryPolicy DEFAULT_POLICY = new RepositoryPolicy();

        static final int ID = 0x0001,
                TYPE = 0x0002,
                URL = 0x0004,
                RELEASES = 0x0008,
                SNAPSHOTS = 0x0010,
                PROXY = 0x0020,
                AUTH = 0x0040,
                MIRRORED = 0x0080,
                REPOMAN = 0x0100,
                BLOCKED = 0x0200,
                INTENT = 0x0400;

        int delta;

        RemoteRepository prototype;

        String id;

        String type;

        String url;

        RepositoryPolicy releasePolicy = DEFAULT_POLICY;

        RepositoryPolicy snapshotPolicy = DEFAULT_POLICY;

        Proxy proxy;

        Authentication authentication;

        List<RemoteRepository> mirroredRepositories;

        boolean repositoryManager;

        boolean blocked;

        Intent intent = Intent.BARE;

        /**
         * Creates a new repository builder.
         *
         * @param id The identifier of the repository, may be {@code null}.
         * @param type The type of the repository, may be {@code null}.
         * @param url The (base) URL of the repository, may be {@code null}.
         */
        public Builder(String id, String type, String url) {
            this.id = (id != null) ? id : "";
            this.type = (type != null) ? type : "";
            this.url = (url != null) ? url : "";
        }

        /**
         * Creates a new repository builder which uses the specified remote repository as a prototype for the new one.
         * All properties which have not been set on the builder will be copied from the prototype when building the
         * repository.
         *
         * @param prototype The remote repository to use as prototype, must not be {@code null}.
         */
        public Builder(RemoteRepository prototype) {
            this.prototype = requireNonNull(prototype, "remote repository prototype cannot be null");
        }

        /**
         * Builds a new remote repository from the current values of this builder. The state of the builder itself
         * remains unchanged.
         *
         * @return The remote repository, never {@code null}.
         */
        public RemoteRepository build() {
            if (prototype != null && delta == 0) {
                return prototype;
            }
            return new RemoteRepository(this);
        }

        private <T> void delta(int flag, T builder, T prototype) {
            boolean equal = Objects.equals(builder, prototype);
            if (equal) {
                delta &= ~flag;
            } else {
                delta |= flag;
            }
        }

        /**
         * Sets the identifier of the repository.
         *
         * @param id The identifier of the repository, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setId(String id) {
            this.id = (id != null) ? id : "";
            if (prototype != null) {
                delta(ID, this.id, prototype.getId());
            }
            return this;
        }

        /**
         * Sets the type of the repository, e.g. "default".
         *
         * @param type The type of the repository, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setContentType(String type) {
            this.type = (type != null) ? type : "";
            if (prototype != null) {
                delta(TYPE, this.type, prototype.getContentType());
            }
            return this;
        }

        /**
         * Sets the (base) URL of the repository.
         *
         * @param url The URL of the repository, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setUrl(String url) {
            this.url = (url != null) ? url : "";
            if (prototype != null) {
                delta(URL, this.url, prototype.getUrl());
            }
            return this;
        }

        /**
         * Sets the policy to apply for snapshot and release artifacts.
         *
         * @param policy The repository policy to set, may be {@code null} to use a default policy.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setPolicy(RepositoryPolicy policy) {
            this.releasePolicy = (policy != null) ? policy : DEFAULT_POLICY;
            this.snapshotPolicy = (policy != null) ? policy : DEFAULT_POLICY;
            if (prototype != null) {
                delta(RELEASES, this.releasePolicy, prototype.getPolicy(false));
                delta(SNAPSHOTS, this.snapshotPolicy, prototype.getPolicy(true));
            }
            return this;
        }

        /**
         * Sets the policy to apply for release artifacts.
         *
         * @param releasePolicy The repository policy to set, may be {@code null} to use a default policy.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setReleasePolicy(RepositoryPolicy releasePolicy) {
            this.releasePolicy = (releasePolicy != null) ? releasePolicy : DEFAULT_POLICY;
            if (prototype != null) {
                delta(RELEASES, this.releasePolicy, prototype.getPolicy(false));
            }
            return this;
        }

        /**
         * Sets the policy to apply for snapshot artifacts.
         *
         * @param snapshotPolicy The repository policy to set, may be {@code null} to use a default policy.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setSnapshotPolicy(RepositoryPolicy snapshotPolicy) {
            this.snapshotPolicy = (snapshotPolicy != null) ? snapshotPolicy : DEFAULT_POLICY;
            if (prototype != null) {
                delta(SNAPSHOTS, this.snapshotPolicy, prototype.getPolicy(true));
            }
            return this;
        }

        /**
         * Sets the proxy to use in order to access the repository.
         *
         * @param proxy The proxy to use, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setProxy(Proxy proxy) {
            this.proxy = proxy;
            if (prototype != null) {
                delta(PROXY, this.proxy, prototype.getProxy());
            }
            return this;
        }

        /**
         * Sets the authentication to use in order to access the repository.
         *
         * @param authentication The authentication to use, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setAuthentication(Authentication authentication) {
            this.authentication = authentication;
            if (prototype != null) {
                delta(AUTH, this.authentication, prototype.getAuthentication());
            }
            return this;
        }

        /**
         * Sets the repositories being mirrored by the repository.
         *
         * @param mirroredRepositories The repositories being mirrored by the repository, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setMirroredRepositories(List<RemoteRepository> mirroredRepositories) {
            if (this.mirroredRepositories == null) {
                this.mirroredRepositories = new ArrayList<>();
            } else {
                this.mirroredRepositories.clear();
            }
            if (mirroredRepositories != null) {
                this.mirroredRepositories.addAll(mirroredRepositories);
            }
            if (prototype != null) {
                delta(MIRRORED, this.mirroredRepositories, prototype.getMirroredRepositories());
            }
            return this;
        }

        /**
         * Adds the specified repository to the list of repositories being mirrored by the repository. If this builder
         * was {@link Builder constructed from a prototype}, the given repository
         * will be added to the list of mirrored repositories from the prototype.
         *
         * @param mirroredRepository The repository being mirrored by the repository, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder addMirroredRepository(RemoteRepository mirroredRepository) {
            if (mirroredRepository != null) {
                if (this.mirroredRepositories == null) {
                    this.mirroredRepositories = new ArrayList<>();
                    if (prototype != null) {
                        mirroredRepositories.addAll(prototype.getMirroredRepositories());
                    }
                }
                mirroredRepositories.add(mirroredRepository);
                if (prototype != null) {
                    delta |= MIRRORED;
                }
            }
            return this;
        }

        /**
         * Marks the repository as a repository manager or not.
         *
         * @param repositoryManager {@code true} if the repository points at a repository manager, {@code false} if the
         *            repository is just serving static contents.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setRepositoryManager(boolean repositoryManager) {
            this.repositoryManager = repositoryManager;
            if (prototype != null) {
                delta(REPOMAN, this.repositoryManager, prototype.isRepositoryManager());
            }
            return this;
        }

        /**
         * Marks the repository as blocked or not.
         *
         * @param blocked {@code true} if the repository should not be allowed to perform any requests.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setBlocked(boolean blocked) {
            this.blocked = blocked;
            if (prototype != null) {
                delta(BLOCKED, this.blocked, prototype.isBlocked());
            }
            return this;
        }

        /**
         * Marks the intent for this repository.
         *
         * @param intent the intent with this remote repository.
         * @return This builder for chaining, never {@code null}.
         * @since 2.0.14
         */
        public Builder setIntent(Intent intent) {
            this.intent = intent;
            if (prototype != null) {
                delta(INTENT, this.intent, prototype.intent);
            }
            return this;
        }
    }
}
