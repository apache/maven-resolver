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

import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages access to a properties file.
 */
@Singleton
@Named
public final class DefaultTrackingFileManager implements TrackingFileManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTrackingFileManager.class);

    @Override
    public Properties read(File file) {
        Path filePath = file.toPath();
        if (Files.isRegularFile(filePath)) {
            try (InputStream stream = Files.newInputStream(filePath)) {
                Properties props = new Properties();
                props.load(stream);
                return props;
            } catch (IOException e) {
                // actually a non readable file is like non existing file...
                fileError("Failed to read tracking file '{}'", file, e);
                /// ... so just fall through.
            }
        }
        return null;
    }

    @Override
    public Properties update(File file, Map<String, String> updates) {
        int retry = 5;
        while (true) {
            Path filePath = file.toPath();
            Properties props = new Properties();

            try {
                Files.createDirectories(filePath.getParent());
            } catch (IOException e) {
                fileError("Failed to create tracking file parent '{}'", file, e);
                props.putAll(updates);
                // if folders can not be created, we can neither read nor write, but simply
                // return what's about to be written here...
                return props;
            }

            try {
                if (Files.isRegularFile(filePath)) {
                    try (InputStream stream = Files.newInputStream(filePath)) {
                        props.load(stream);
                    } catch (IOException e) {
                        fileError("Failed to load current tracking file content '{}'", file, e);
                        // ... then we can only assume no recoverable properties... move on!
                    }
                }

                for (Map.Entry<String, String> update : updates.entrySet()) {
                    if (update.getValue() == null) {
                        props.remove(update.getKey());
                    } else {
                        props.setProperty(update.getKey(), update.getValue());
                    }
                }

                FileUtils.writeFile(filePath, p -> {
                    try (OutputStream stream = Files.newOutputStream(p)) {
                        LOGGER.debug("Writing tracking file '{}'", file);
                        props.store(
                                stream,
                                "NOTE: This is a Maven Resolver internal implementation file"
                                        + ", its format can be changed without prior notice.");
                    }
                });
            } catch (IOException e) {
                if (retry-- > 0) {
                    try {
                        TimeUnit.MILLISECONDS.sleep((long) ((500 * Math.random()) + 10));
                    } catch (InterruptedException e1) {
                        Thread.currentThread().interrupt();
                        return props;
                    }
                    continue;
                }
                fileError("Failed to write tracking file '{}'", file, e);
                // we have tried hard enough without any success ...
            }
            return props;
        }
    }

    private void fileError(String msg, File file, IOException e) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.warn(msg, file, e);
        } else {
            LOGGER.warn(msg, file);
        }
    }
}
