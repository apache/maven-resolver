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

import static java.util.Objects.requireNonNull;

/**
 * A range of versions.
 */
public interface VersionRange {

    /**
     * Determines whether the specified version is contained within this range.
     *
     * @param version The version to test, must not be {@code null}.
     * @return {@code true} if this range contains the specified version, {@code false} otherwise.
     */
    boolean containsVersion(Version version);

    /**
     * Gets a lower bound (if any) for this range. If existent, this range does not contain any version smaller than its
     * lower bound. Note that complex version ranges might exclude some versions even within their bounds.
     *
     * @return A lower bound for this range or {@code null} is there is none.
     */
    Bound getLowerBound();

    /**
     * Gets an upper bound (if any) for this range. If existent, this range does not contain any version greater than
     * its upper bound. Note that complex version ranges might exclude some versions even within their bounds.
     *
     * @return An upper bound for this range or {@code null} is there is none.
     */
    Bound getUpperBound();

    /**
     * A bound of a version range.
     */
    final class Bound {

        private final Version version;

        private final boolean inclusive;

        /**
         * Creates a new bound with the specified properties.
         *
         * @param version The bounding version, must not be {@code null}.
         * @param inclusive A flag whether the specified version is included in the range or not.
         */
        public Bound(Version version, boolean inclusive) {
            this.version = requireNonNull(version, "version cannot be null");
            this.inclusive = inclusive;
        }

        /**
         * Gets the bounding version.
         *
         * @return The bounding version, never {@code null}.
         */
        public Version getVersion() {
            return version;
        }

        /**
         * Indicates whether the bounding version is included in the range or not.
         *
         * @return {@code true} if the bounding version is included in the range, {@code false} if not.
         */
        public boolean isInclusive() {
            return inclusive;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj == null || !getClass().equals(obj.getClass())) {
                return false;
            }

            Bound that = (Bound) obj;
            return inclusive == that.inclusive && version.equals(that.version);
        }

        @Override
        public int hashCode() {
            int hash = 17;
            hash = hash * 31 + version.hashCode();
            hash = hash * 31 + (inclusive ? 1 : 0);
            return hash;
        }

        @Override
        public String toString() {
            return String.valueOf(version);
        }
    }
}
