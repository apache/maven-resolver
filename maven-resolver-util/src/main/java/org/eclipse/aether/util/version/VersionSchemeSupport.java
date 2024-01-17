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
package org.eclipse.aether.util.version;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.VersionRange;
import org.eclipse.aether.version.VersionScheme;

import static java.util.Objects.requireNonNull;

/**
 * A version scheme support class. A new implementation should extend this class and would receive full support for
 * ranges and constraints. The new implementation should implement {@link org.eclipse.aether.version.Version} and
 * the one missing method in this class, the {@link #parseVersion(String)}.
 *
 * @since 2.0.0
 */
abstract class VersionSchemeSupport implements VersionScheme {
    @Override
    public GenericVersionRange parseVersionRange(final String range) throws InvalidVersionSpecificationException {
        return new GenericVersionRange(this, range);
    }

    @Override
    public GenericVersionConstraint parseVersionConstraint(final String constraint)
            throws InvalidVersionSpecificationException {
        String process = requireNonNull(constraint, "constraint cannot be null");

        Collection<VersionRange> ranges = new ArrayList<>();

        while (process.startsWith("[") || process.startsWith("(")) {
            int index1 = process.indexOf(')');
            int index2 = process.indexOf(']');

            int index = index2;
            if (index2 < 0 || (index1 >= 0 && index1 < index2)) {
                index = index1;
            }

            if (index < 0) {
                throw new InvalidVersionSpecificationException(constraint, "Unbounded version range " + constraint);
            }

            VersionRange range = parseVersionRange(process.substring(0, index + 1));
            ranges.add(range);

            process = process.substring(index + 1).trim();

            if (process.startsWith(",")) {
                process = process.substring(1).trim();
            }
        }

        if (!process.isEmpty() && !ranges.isEmpty()) {
            throw new InvalidVersionSpecificationException(
                    constraint, "Invalid version range " + constraint + ", expected [ or ( but got " + process);
        }

        GenericVersionConstraint result;
        if (ranges.isEmpty()) {
            result = new GenericVersionConstraint(parseVersion(constraint));
        } else {
            result = new GenericVersionConstraint(UnionVersionRange.from(ranges));
        }

        return result;
    }
}
