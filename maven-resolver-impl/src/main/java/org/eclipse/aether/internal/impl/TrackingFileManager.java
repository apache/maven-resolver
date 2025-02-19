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

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Manages access to a properties file.
 */
public interface TrackingFileManager {
    /**
     * Reads up the specified properties file into {@link Properties}, if exists, otherwise {@code null} is returned.
     * @deprecated Use {@link #read(Path)} instead.
     */
    @Deprecated
    Properties read(File file);

    /**
     * Reads up the specified properties file into {@link Properties}, if exists, otherwise {@code null} is returned.
     * @since 2.0.0
     */
    Properties read(Path path);

    /**
     * Applies updates to specified properties file and returns resulting {@link Properties} with contents same
     * as in updated file, never {@code null}.
     * @deprecated Use {@link #update(Path, Map)} instead.
     */
    @Deprecated
    Properties update(File file, Map<String, String> updates);

    /**
     * Applies updates to specified properties file and returns resulting {@link Properties} with contents same
     * as in updated file, never {@code null}.
     * @since 2.0.0
     */
    Properties update(Path path, Map<String, String> updates);
}
