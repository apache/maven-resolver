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
package org.eclipse.aether.named.redisson;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.eclipse.aether.named.support.NamedLockFactorySupport;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * Support class for factories using {@link RedissonClient}.
 */
public abstract class RedissonNamedLockFactorySupport extends NamedLockFactorySupport {
    protected static final String NAME_PREFIX = "maven:resolver:";

    private static final String DEFAULT_CONFIG_FILE_NAME = "maven-resolver-redisson.yaml";

    private static final String DEFAULT_CLIENT_NAME = "maven-resolver";

    /**
     * Path to a Redisson configuration file in YAML format. Read official documentation for details.
     *
     * @since 1.7.0
     * @configurationSource {@link System#getProperty(String, String)}
     * @configurationType {@link java.lang.String}
     */
    public static final String SYSTEM_PROP_CONFIG_FILE = "aether.syncContext.named.redisson.configFile";

    /**
     * Address of the Redis instance. Optional.
     *
     * @since 2.0.0
     * @configurationSource {@link System#getProperty(String, String)}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_REDIS_ADDRESS}
     */
    public static final String SYSTEM_PROP_REDIS_ADDRESS = "aether.syncContext.named.redisson.address";

    public static final String DEFAULT_REDIS_ADDRESS = "redis://localhost:6379";

    /**
     * Whether a plaintext {@code redis://} address pointing at a non-loopback host is allowed. Such a connection is
     * unencrypted and unauthenticated at the transport, so a tampered, spoofed, or on-path-modified Redis can grant
     * conflicting locks and corrupt the shared local repository the locks protect. Disabled by default; prefer a
     * {@code rediss://} (TLS) address, or a Redisson configuration file with authentication, for anything
     * cross-host.
     *
     * @since 2.0.23
     * @configurationSource {@link System#getProperty(String, String)}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue false
     */
    public static final String SYSTEM_PROP_ALLOW_INSECURE_ADDRESS =
            "aether.syncContext.named.redisson.allowInsecureAddress";

    protected final RedissonClient redissonClient;

    public RedissonNamedLockFactorySupport() {
        this.redissonClient = createRedissonClient();
    }

    @Override
    protected void doShutdown() {
        logger.trace("Shutting down Redisson client with id '{}'", redissonClient.getId());
        redissonClient.shutdown();
    }

    private RedissonClient createRedissonClient() {
        Path configFilePath = null;

        String configFile = System.getProperty(SYSTEM_PROP_CONFIG_FILE);
        if (configFile != null && !configFile.isEmpty()) {
            configFilePath = Paths.get(configFile);
            if (Files.notExists(configFilePath)) {
                throw new IllegalArgumentException(
                        "The specified Redisson config file does not exist: " + configFilePath);
            }
        }

        if (configFilePath == null) {
            String mavenConf = System.getProperty("maven.conf");
            if (mavenConf != null && !mavenConf.isEmpty()) {
                configFilePath = Paths.get(mavenConf, DEFAULT_CONFIG_FILE_NAME);
                if (Files.notExists(configFilePath)) {
                    configFilePath = null;
                }
            }
        }

        Config config;

        if (configFilePath != null) {
            logger.trace("Reading Redisson config file from '{}'", configFilePath);
            try (InputStream is = Files.newInputStream(configFilePath)) {
                config = Config.fromYAML(is);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read Redisson config file: " + configFilePath, e);
            }
        } else {
            config = new Config();
            String defaultRedisAddress = System.getProperty(SYSTEM_PROP_REDIS_ADDRESS, DEFAULT_REDIS_ADDRESS);
            if (isInsecureRemoteAddress(defaultRedisAddress)) {
                if (Boolean.getBoolean(SYSTEM_PROP_ALLOW_INSECURE_ADDRESS)) {
                    logger.warn(
                            "Using plaintext Redis address '{}' for lock state guarding local repository writes;"
                                    + " the connection is unencrypted and unauthenticated at the transport, so the"
                                    + " endpoint and the network path to it must be trusted and isolated",
                            defaultRedisAddress);
                } else {
                    throw new IllegalStateException("Refusing plaintext non-loopback Redis address '"
                            + defaultRedisAddress + "': lock answers from a tampered or spoofed Redis can void"
                            + " mutual exclusion and corrupt the shared local repository. Use a 'rediss://' (TLS)"
                            + " address, or a Redisson configuration file ('" + SYSTEM_PROP_CONFIG_FILE
                            + "') with authentication, or explicitly opt in with -D"
                            + SYSTEM_PROP_ALLOW_INSECURE_ADDRESS + "=true");
                }
            }
            config.useSingleServer().setAddress(defaultRedisAddress).setClientName(DEFAULT_CLIENT_NAME);
        }

        RedissonClient redissonClient = Redisson.create(config);
        logger.trace("Created Redisson client with id '{}'", redissonClient.getId());

        return redissonClient;
    }

    /**
     * Returns {@code true} if the given address is a plaintext {@code redis://} address pointing at a non-loopback
     * host. TLS ({@code rediss://}) addresses and loopback addresses are acceptable defaults; anything else is
     * insecure. Unparseable addresses are treated as insecure (fail closed).
     */
    static boolean isInsecureRemoteAddress(String address) {
        if (address.toLowerCase(Locale.ROOT).startsWith("rediss://")) {
            return false; // TLS protects the channel
        }
        String host;
        try {
            host = URI.create(address).getHost();
        } catch (IllegalArgumentException e) {
            return true;
        }
        if (host == null) {
            return true;
        }
        return !("localhost".equalsIgnoreCase(host)
                || host.startsWith("127.")
                || "::1".equals(host)
                || "[::1]".equals(host));
    }
}
