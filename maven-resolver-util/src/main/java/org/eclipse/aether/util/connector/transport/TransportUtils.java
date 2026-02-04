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
package org.eclipse.aether.util.connector.transport;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.ConfigUtils;

/**
 * A utility class to read transport-related configuration. It implements all transport related configurations from
 * {@link ConfigurationProperties} and transport implementations are free to use those that are supported by themselves.
 *
 * @see ConfigurationProperties
 * @see RepositorySystemSession#getConfigProperties()
 * @since 2.0.15
 */
public final class TransportUtils {
    private TransportUtils() {}

    /**
     * Getter for {@link ConfigurationProperties#USER_AGENT}.
     */
    public static String getUserAgent(RepositorySystemSession session, RemoteRepository repository) {
        return ConfigUtils.getString(
                session,
                ConfigurationProperties.DEFAULT_USER_AGENT,
                ConfigurationProperties.USER_AGENT,
                "aether.connector.userAgent");
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTPS_SECURITY_MODE}.
     */
    public static String getHttpsSecurityMode(RepositorySystemSession session, RemoteRepository repository) {
        String result = ConfigUtils.getString(
                session,
                ConfigurationProperties.HTTPS_SECURITY_MODE_DEFAULT,
                ConfigurationProperties.HTTPS_SECURITY_MODE + "." + repository.getId(),
                ConfigurationProperties.HTTPS_SECURITY_MODE);
        if (!ConfigurationProperties.HTTPS_SECURITY_MODE_DEFAULT.equals(result)
                && !ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE.equals(result)) {
            throw new IllegalArgumentException("Unsupported '" + result + "' HTTPS security mode.");
        }
        return result;
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_CONNECTION_MAX_TTL}.
     */
    public static int getHttpConnectionMaxTtlSeconds(RepositorySystemSession session, RemoteRepository repository) {
        int result = ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_HTTP_CONNECTION_MAX_TTL,
                ConfigurationProperties.HTTP_CONNECTION_MAX_TTL + "." + repository.getId(),
                ConfigurationProperties.HTTP_CONNECTION_MAX_TTL);
        if (result < 0) {
            throw new IllegalArgumentException(ConfigurationProperties.HTTP_CONNECTION_MAX_TTL + " value must be >= 0");
        }
        return result;
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_MAX_CONNECTIONS_PER_ROUTE}.
     */
    public static int getHttpMaxConnectionsPerRoute(RepositorySystemSession session, RemoteRepository repository) {
        int result = ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_HTTP_MAX_CONNECTIONS_PER_ROUTE,
                ConfigurationProperties.HTTP_MAX_CONNECTIONS_PER_ROUTE + "." + repository.getId(),
                ConfigurationProperties.HTTP_MAX_CONNECTIONS_PER_ROUTE);
        if (result < 1) {
            throw new IllegalArgumentException(
                    ConfigurationProperties.HTTP_MAX_CONNECTIONS_PER_ROUTE + " value must be > 0");
        }
        return result;
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_HEADERS}.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getHttpHeaders(RepositorySystemSession session, RemoteRepository repository) {
        return (Map<String, String>) ConfigUtils.getMap(
                session,
                Collections.emptyMap(),
                ConfigurationProperties.HTTP_HEADERS + "." + repository.getId(),
                ConfigurationProperties.HTTP_HEADERS);
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_PREEMPTIVE_AUTH}.
     */
    public static boolean isHttpPreemptiveAuth(RepositorySystemSession session, RemoteRepository repository) {
        return ConfigUtils.getBoolean(
                session,
                ConfigurationProperties.DEFAULT_HTTP_PREEMPTIVE_AUTH,
                ConfigurationProperties.HTTP_PREEMPTIVE_AUTH + "." + repository.getId(),
                ConfigurationProperties.HTTP_PREEMPTIVE_AUTH);
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_PREEMPTIVE_PUT_AUTH}.
     */
    public static boolean isHttpPreemptivePutAuth(RepositorySystemSession session, RemoteRepository repository) {
        return ConfigUtils.getBoolean(
                session,
                ConfigurationProperties.DEFAULT_HTTP_PREEMPTIVE_PUT_AUTH,
                ConfigurationProperties.HTTP_PREEMPTIVE_PUT_AUTH + "." + repository.getId(),
                ConfigurationProperties.HTTP_PREEMPTIVE_PUT_AUTH);
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_SUPPORT_WEBDAV}.
     */
    public static boolean isHttpSupportWebDav(RepositorySystemSession session, RemoteRepository repository) {
        return ConfigUtils.getBoolean(
                session,
                ConfigurationProperties.DEFAULT_HTTP_SUPPORT_WEBDAV,
                ConfigurationProperties.HTTP_SUPPORT_WEBDAV + "." + repository.getId(),
                ConfigurationProperties.HTTP_SUPPORT_WEBDAV);
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_CREDENTIAL_ENCODING}.
     */
    public static Charset getHttpCredentialsEncoding(RepositorySystemSession session, RemoteRepository repository) {
        return Charset.forName(ConfigUtils.getString(
                session,
                ConfigurationProperties.DEFAULT_HTTP_CREDENTIAL_ENCODING,
                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING + "." + repository.getId(),
                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING));
    }

    /**
     * Getter for {@link ConfigurationProperties#CONNECT_TIMEOUT}.
     */
    public static int getHttpConnectTimeout(RepositorySystemSession session, RemoteRepository repository) {
        return ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT,
                ConfigurationProperties.CONNECT_TIMEOUT + "." + repository.getId(),
                ConfigurationProperties.CONNECT_TIMEOUT);
    }

    /**
     * Getter for {@link ConfigurationProperties#REQUEST_TIMEOUT}.
     */
    public static int getHttpRequestTimeout(RepositorySystemSession session, RemoteRepository repository) {
        return ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT,
                ConfigurationProperties.REQUEST_TIMEOUT + "." + repository.getId(),
                ConfigurationProperties.REQUEST_TIMEOUT);
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_RETRY_HANDLER_COUNT}.
     */
    public static int getHttpRetryHandlerCount(RepositorySystemSession session, RemoteRepository repository) {
        int result = ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_HTTP_RETRY_HANDLER_COUNT,
                ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT + "." + repository.getId(),
                ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT);
        if (result < 0) {
            throw new IllegalArgumentException(
                    ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT + " value must be >= 0");
        }
        return result;
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_RETRY_HANDLER_INTERVAL}.
     */
    public static long getHttpRetryHandlerInterval(RepositorySystemSession session, RemoteRepository repository) {
        long result = ConfigUtils.getLong(
                session,
                ConfigurationProperties.DEFAULT_HTTP_RETRY_HANDLER_INTERVAL,
                ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL + "." + repository.getId(),
                ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL);
        if (result < 0) {
            throw new IllegalArgumentException(
                    ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL + " value must be >= 0");
        }
        return result;
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_RETRY_HANDLER_INTERVAL_MAX}.
     */
    public static long getHttpRetryHandlerIntervalMax(RepositorySystemSession session, RemoteRepository repository) {
        long result = ConfigUtils.getLong(
                session,
                ConfigurationProperties.DEFAULT_HTTP_RETRY_HANDLER_INTERVAL_MAX,
                ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL_MAX + "." + repository.getId(),
                ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL_MAX);
        if (result < 0) {
            throw new IllegalArgumentException(
                    ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL_MAX + " value must be >= 0");
        }
        return result;
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_EXPECT_CONTINUE}.
     */
    public static Optional<Boolean> getHttpExpectContinue(
            RepositorySystemSession session, RemoteRepository repository) {
        String expectContinue = ConfigUtils.getString(
                session,
                null,
                ConfigurationProperties.HTTP_EXPECT_CONTINUE + "." + repository.getId(),
                ConfigurationProperties.HTTP_EXPECT_CONTINUE);
        if (expectContinue != null) {
            return Optional.of(Boolean.parseBoolean(expectContinue));
        }
        return Optional.empty();
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_REUSE_CONNECTIONS}.
     */
    public static boolean isHttpReuseConnections(RepositorySystemSession session, RemoteRepository repository) {
        return ConfigUtils.getBoolean(
                session,
                ConfigurationProperties.DEFAULT_HTTP_REUSE_CONNECTIONS,
                ConfigurationProperties.HTTP_REUSE_CONNECTIONS + "." + repository.getId(),
                ConfigurationProperties.HTTP_REUSE_CONNECTIONS);
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE}.
     */
    public static Set<Integer> getHttpServiceUnavailableCodes(
            RepositorySystemSession session, RemoteRepository repository) {
        String stringValue = ConfigUtils.getString(
                session,
                ConfigurationProperties.DEFAULT_HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE,
                ConfigurationProperties.HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE + "." + repository.getId(),
                ConfigurationProperties.HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE);
        Set<Integer> result = new HashSet<>();
        try {
            for (String code : ConfigUtils.parseCommaSeparatedUniqueNames(stringValue)) {
                result.add(Integer.parseInt(code));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Illegal HTTP codes for " + ConfigurationProperties.HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE
                            + " (list of integers): " + stringValue);
        }
        return result;
    }

    /**
     * Getter for {@link ConfigurationProperties#HTTP_LOCAL_ADDRESS}.
     */
    public static Optional<InetAddress> getHttpLocalAddress(
            RepositorySystemSession session, RemoteRepository repository) {
        String bindAddress = ConfigUtils.getString(
                session,
                null,
                ConfigurationProperties.HTTP_LOCAL_ADDRESS + "." + repository.getId(),
                ConfigurationProperties.HTTP_LOCAL_ADDRESS);
        if (bindAddress != null) {
            try {
                return Optional.of(InetAddress.getByName(bindAddress));
            } catch (UnknownHostException uhe) {
                throw new IllegalArgumentException(
                        "Given bind address (" + bindAddress + ") cannot be resolved for remote repository "
                                + repository,
                        uhe);
            }
        }
        return Optional.empty();
    }
}
