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
package org.eclipse.aether.transport.wagon;

import org.apache.maven.wagon.Wagon;

/**
 * A component to acquire and release wagon instances for uploads/downloads.
 */
public interface WagonProvider {

    /**
     * Acquires a wagon instance that matches the specified role hint. The role hint is derived from the URI scheme,
     * e.g. "http" or "file".
     *
     * @param roleHint The role hint to get a wagon for, must not be {@code null}.
     * @return The requested wagon instance, never {@code null}.
     * @throws Exception If no wagon could be retrieved for the specified role hint.
     */
    Wagon lookup(String roleHint) throws Exception;

    /**
     * Releases the specified wagon. A wagon provider may either free any resources allocated for the wagon instance or
     * return the instance back to a pool for future use.
     *
     * @param wagon The wagon to release, may be {@code null}.
     */
    void release(Wagon wagon);
}
