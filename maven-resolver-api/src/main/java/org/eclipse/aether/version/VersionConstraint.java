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
package org.eclipse.aether.version;

/**
 * A constraint on versions for a dependency. A constraint can either consist of a version range (e.g. "[1, ]") or a
 * single version (e.g. "1.1"). In the first case, the constraint expresses a hard requirement on a version matching the
 * range. In the second case, the constraint expresses a soft requirement on a specific version (i.e. a recommendation).
 */
public interface VersionConstraint {

    /**
     * Gets the version range of this constraint.
     *
     * @return The version range or {@code null} if none.
     */
    VersionRange getRange();

    /**
     * Gets the version recommended by this constraint.
     *
     * @return The recommended version or {@code null} if none.
     */
    Version getVersion();

    /**
     * Determines whether the specified version satisfies this constraint. In more detail, a version satisfies this
     * constraint if it matches its version range or if this constraint has no version range and the specified version
     * equals the version recommended by the constraint.
     *
     * @param version The version to test, must not be {@code null}.
     * @return {@code true} if the specified version satisfies this constraint, {@code false} otherwise.
     */
    boolean containsVersion(Version version);
}
