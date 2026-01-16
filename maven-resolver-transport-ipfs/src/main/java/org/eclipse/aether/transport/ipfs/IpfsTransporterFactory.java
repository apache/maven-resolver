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
package org.eclipse.aether.transport.ipfs;

import javax.inject.Named;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.ipfs.api.IPFS;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * A transporter factory for repositories using the {@code ipfs:namespace[/namespacePrefix]} URIs.
 * <p>
 * It is recommended to have namespace equal to artifacts groupId prefix, for example artifacts with groupId
 * {@code org.apache.maven.plugins} should be published into {@code org.apache} or {@code org.apache.maven} or
 * {@code org.apache.maven.plugins} namespace.
 *
 * @since 2.0.15
 */
@Named(IpfsTransporterFactory.NAME)
public final class IpfsTransporterFactory implements TransporterFactory {
    public static final String NAME = "ipfs";

    private static final String PROTO = NAME + ":";
    private static final int PROTO_LEN = PROTO.length();

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private float priority;
    private final ConcurrentMap<RemoteRepository, IpfsNamespacePublisher> ongoingPublishing = new ConcurrentHashMap<>();

    @Override
    public float getPriority() {
        return priority;
    }

    /**
     * Sets the priority of this component.
     *
     * @param priority The priority.
     * @return This component for chaining, never {@code null}.
     */
    public IpfsTransporterFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }

    /**
     * Creates new instance of {@link IpfsTransporter}.
     *
     * @param session The session.
     * @param repository The remote repository.
     */
    @Override
    public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoTransporterException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");

        String repositoryUrl = repository.getUrl();
        if (repositoryUrl.startsWith(PROTO)) {
            repositoryUrl = repositoryUrl.substring(PROTO_LEN);
            while (repositoryUrl.startsWith("/")) {
                repositoryUrl = repositoryUrl.substring(1);
            }

            if (repositoryUrl.trim().isEmpty()) {
                throw new NoTransporterException(
                        repository,
                        "Invalid IPFS URL; should be ipfs:namespace[/namespacePrefix] where no segment can be empty string");
            }

            String namespace;
            String namespacePrefix;
            if (repositoryUrl.contains("/")) {
                int firstSlash = repositoryUrl.indexOf("/");
                namespace = repositoryUrl.substring(0, firstSlash);
                namespacePrefix = repositoryUrl.substring(firstSlash + 1);
            } else {
                namespace = repositoryUrl;
                namespacePrefix = "";
            }

            String multiaddr = ConfigUtils.getString(
                    session.getConfigProperties(),
                    IpfsTransporterConfigurationKeys.DEFAULT_MULTIADDR,
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_MULTIADDR + "." + repository.getId(),
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_MULTIADDR);
            String filesPrefix = ConfigUtils.getString(
                    session.getConfigProperties(),
                    IpfsTransporterConfigurationKeys.DEFAULT_FILES_PREFIX,
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_FILES_PREFIX + "." + repository.getId(),
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_FILES_PREFIX);
            boolean refreshIpns = ConfigUtils.getBoolean(
                    session.getConfigProperties(),
                    IpfsTransporterConfigurationKeys.DEFAULT_REFRESH_IPNS,
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_REFRESH_IPNS + "." + repository.getId(),
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_REFRESH_IPNS);
            boolean publishIpns = ConfigUtils.getBoolean(
                    session.getConfigProperties(),
                    IpfsTransporterConfigurationKeys.DEFAULT_PUBLISH_IPNS,
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_PUBLISH_IPNS + "." + repository.getId(),
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_PUBLISH_IPNS);
            String publishIpnsKeyName = ConfigUtils.getString(
                    session.getConfigProperties(),
                    namespace,
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_PUBLISH_IPNS_KEY_NAME + "." + repository.getId(),
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_PUBLISH_IPNS_KEY_NAME);
            boolean publishIpnsKeyCreate = ConfigUtils.getBoolean(
                    session.getConfigProperties(),
                    IpfsTransporterConfigurationKeys.DEFAULT_PUBLISH_IPNS_KEY_CREATE,
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_PUBLISH_IPNS_KEY_CREATE + "." + repository.getId(),
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_PUBLISH_IPNS_KEY_CREATE);
            boolean namespaceIsPrefix = ConfigUtils.getBoolean(
                    session.getConfigProperties(),
                    IpfsTransporterConfigurationKeys.DEFAULT_NAMESPACE_IS_PREFIX,
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_NAMESPACE_IS_PREFIX + "." + repository.getId(),
                    IpfsTransporterConfigurationKeys.CONFIG_PROP_NAMESPACE_IS_PREFIX);

            IPFS ipfs = connect(multiaddr);
            IpfsNamespacePublisher publisher =
                    ongoingPublishing.computeIfAbsent(repository.toBareRemoteRepository(), k -> {
                        IpfsNamespacePublisher pub = new IpfsNamespacePublisher(
                                ipfs,
                                namespace,
                                filesPrefix,
                                namespacePrefix,
                                publishIpnsKeyName,
                                publishIpnsKeyCreate);
                        if (refreshIpns) {
                            try {
                                if (!pub.refreshNamespace()) {
                                    logger.warn("IPNS refresh unsuccessful, see logs above for reasons");
                                }
                            } catch (IOException e) {
                                logger.warn("IPNS refresh failed", e);
                            }
                        }
                        if (publishIpns) {
                            session.addOnSessionEndedHandler(() -> {
                                try {
                                    if (!ongoingPublishing.remove(repository).publishNamespace()) {
                                        logger.warn("IPNS publish unsuccessful, see logs above for reasons");
                                    }
                                } catch (IOException e) {
                                    logger.warn("IPNS publishing failed", e);
                                }
                            });
                        }
                        return pub;
                    });
            return new IpfsTransporter(publisher, namespace, namespaceIsPrefix);
        }
        throw new NoTransporterException(repository);
    }

    @SuppressWarnings("rawtypes")
    private IPFS connect(String multiaddr) {
        try {
            IPFS ipfs = new IPFS(multiaddr);
            Map id = ipfs.id();
            logger.debug("Connected to IPFS w/ ID={} node at '{}'", id.get("ID"), multiaddr);
            return ipfs;
        } catch (IOException e) {
            // this is user error: bad multiaddr or daemon does not run; hard failure
            throw new UncheckedIOException(e);
        }
    }
}
