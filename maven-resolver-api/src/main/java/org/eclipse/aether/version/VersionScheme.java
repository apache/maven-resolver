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
 * A version scheme that handles interpretation of version strings to facilitate their comparison.
 */
public interface VersionScheme {

    /**
     * Parses the specified version string, for example "1.0".
     *
     * @param version The version string to parse, must not be {@code null}.
     * @return The parsed version, never {@code null}.
     * @throws InvalidVersionSpecificationException If the string violates the syntax rules of this scheme.
     */
    Version parseVersion(String version) throws InvalidVersionSpecificationException;

    /**
     * Parses the specified version range specification, for example "[1.0,2.0)".
     *
     * @param range The range specification to parse, must not be {@code null}.
     * @return The parsed version range, never {@code null}.
     * @throws InvalidVersionSpecificationException If the range specification violates the syntax rules of this scheme.
     */
    VersionRange parseVersionRange(String range) throws InvalidVersionSpecificationException;

    /**
     * Parses the specified version constraint specification, for example "1.0" or "[1.0,2.0),(2.0,)".
     *
     * @param constraint The constraint specification to parse, must not be {@code null}.
     * @return The parsed version constraint, never {@code null}.
     * @throws InvalidVersionSpecificationException If the constraint specification violates the syntax rules of this
     *             scheme.
     */
    VersionConstraint parseVersionConstraint(String constraint) throws InvalidVersionSpecificationException;
}
