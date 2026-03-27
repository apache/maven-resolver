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
import javax.inject.Provider;
import javax.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.impl.NamedLockFactorySelector;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Provides selected instance of {@link TrackingFileManager} implementation.
 *
 * @since 2.0.17
 */
@Singleton
@Named
public class TrackingFileManagerProvider implements Provider<TrackingFileManager> {
    public static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_SYSTEM + "trackingFileManager.";

    /**
     * Name of the tracking file manager to use. Supported values are "namedLocks" and "legacy". The latter should be
     * used if it is known, that local repository is simultaneously accessed by Maven 3.10+ and older Maven versions.
     * This decision happens early, during boot of the system, hence system properties can be used only as configuration
     * source.
     *
     * @configurationSource {@link System#getProperty(String, String)}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_TRACKING_FILE_MANAGER_NAME}
     */
    public static final String CONFIG_PROP_TRACKING_FILE_MANAGER_NAME = CONFIG_PROPS_PREFIX + "name";

    public static final String DEFAULT_TRACKING_FILE_MANAGER_NAME = "legacy";

    private final TrackingFileManager trackingFileManager;

    /**
     * Default constructor, to be used in tests; provides "legacy" tracking file manager only.
     */
    public TrackingFileManagerProvider() {
        this.trackingFileManager = new LegacyTrackingFileManager();
    }

    /**
     * Constructor to be used in production.
     */
    @Inject
    public TrackingFileManagerProvider(NamedLockFactorySelector selector) {
        // this is early construction; no session, hence we must rely on system properties instead
        Map<String, String> config = new HashMap<>();
        for (String name : System.getProperties().stringPropertyNames()) {
            config.put(name, System.getProperty(name));
        }
        String tfmName = ConfigUtils.getString(
                config, DEFAULT_TRACKING_FILE_MANAGER_NAME, CONFIG_PROP_TRACKING_FILE_MANAGER_NAME);
        if ("legacy".equals(tfmName)) {
            this.trackingFileManager = new LegacyTrackingFileManager();
        } else if ("namedLocks".equals(tfmName)) {
            NamedLockFactory factory = selector.getNamedLockFactory(config);
            long time = selector.getLockWaitTime(config);
            TimeUnit timeUnit = selector.getLockWaitTimeUnit(config);
            this.trackingFileManager = new NamedLocksTrackingFileManager(factory, time, timeUnit);
        } else {
            throw new IllegalArgumentException("Unknown tracking file manager name: " + tfmName);
        }
    }

    @Override
    public TrackingFileManager get() {
        return trackingFileManager;
    }
}
