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
package org.eclipse.aether.spi.platform;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.version.Version;

import java.util.List;

/**
 * A definition of platform; it can span multiple "levels", from "what BOM offers" to (optionally) much more.
 */
public interface Platform {
    /**
     * Platform mode of operation.
     */
    enum Mode {
        /**
         * In this mode platform "only" makes sure that platform constituents are processed together, "logically grouped".
         */
        RELAXED,
        /**
         * In this mode platform aside of "logically grouping", also makes versioning strict, enforcing that versions
         * do not fall outside of platform defined versions.
         */
        STRICT
    }

    /**
     * Platform key.
     */
    String getPlatformKey();

    /**
     * Platform name (for human consumption).
     */
    String getPlatformName();

    /**
     * The mode this platform was set up for.
     */
    Mode getPlatformMode();

    /**
     * The version of this platform.
     */
    Version getPlatformVersion();


    /**
     * The managed dependencies this platform contains.
     */
    List<Dependency> getManagedDependencies();

    // checksums
    // relocations
    // repositories
}
