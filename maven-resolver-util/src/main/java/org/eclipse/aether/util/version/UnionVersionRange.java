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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionRange;

/**
 * A union of version ranges.
 */
public final class UnionVersionRange implements VersionRange {

    private final Set<VersionRange> ranges;

    private final Bound lowerBound;

    private final Bound upperBound;

    /**
     * Creates union {@link VersionRange}s out of passed in {@link VersionRange} instances.
     *
     * @param ranges The ranges, may be empty array or even {@code null}.
     */
    public static VersionRange from(VersionRange... ranges) {
        if (ranges == null || ranges.length == 0) {
            return from(Collections.emptySet());
        }
        return from(Arrays.asList(ranges));
    }

    /**
     * Creates union {@link VersionRange}s out of passed in {@link VersionRange} collection.
     *
     * @param ranges The ranges, may be empty collection or even {@code null}.
     */
    public static VersionRange from(Collection<? extends VersionRange> ranges) {
        if (ranges != null && ranges.size() == 1) {
            return ranges.iterator().next();
        }
        return new UnionVersionRange(ranges);
    }

    private UnionVersionRange(Collection<? extends VersionRange> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            this.ranges = Collections.emptySet();
            lowerBound = null;
            upperBound = null;
        } else {
            this.ranges = new HashSet<>(ranges);
            Bound lowerBound = null, upperBound = null;
            for (VersionRange range : this.ranges) {
                Bound lb = range.getLowerBound();
                if (lb == null) {
                    lowerBound = null;
                    break;
                } else if (lowerBound == null) {
                    lowerBound = lb;
                } else {
                    int c = lb.getVersion().compareTo(lowerBound.getVersion());
                    if (c < 0 || (c == 0 && !lowerBound.isInclusive())) {
                        lowerBound = lb;
                    }
                }
            }
            for (VersionRange range : this.ranges) {
                Bound ub = range.getUpperBound();
                if (ub == null) {
                    upperBound = null;
                    break;
                } else if (upperBound == null) {
                    upperBound = ub;
                } else {
                    int c = ub.getVersion().compareTo(upperBound.getVersion());
                    if (c > 0 || (c == 0 && !upperBound.isInclusive())) {
                        upperBound = ub;
                    }
                }
            }
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }
    }

    @Override
    public boolean containsVersion(Version version) {
        for (VersionRange range : ranges) {
            if (range.containsVersion(version)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Bound getLowerBound() {
        return lowerBound;
    }

    @Override
    public Bound getUpperBound() {
        return upperBound;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj == null || !getClass().equals(obj.getClass())) {
            return false;
        }

        UnionVersionRange that = (UnionVersionRange) obj;

        return ranges.equals(that.ranges);
    }

    @Override
    public int hashCode() {
        return 97 * ranges.hashCode();
    }

    @Override
    public String toString() {
        return ranges.stream().map(VersionRange::toString).collect(Collectors.joining(", "));
    }
}
