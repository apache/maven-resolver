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
package org.eclipse.aether.util.graph.version;

import java.util.function.Predicate;

import org.eclipse.aether.util.version.GenericQualifiers;

/**
 * A version filter that (unconditionally) blocks based on qualifiers, as defined by {@link GenericQualifiers}.
 *
 * @since 2.0.17
 */
public class GenericQualifiersVersionFilter extends VersionPredicateVersionFilter {
    /**
     * Filters any version that contains "preview" qualifiers (alpha, beta, milestone).
     */
    public static GenericQualifiersVersionFilter previewVersionFilter() {
        return new GenericQualifiersVersionFilter(i -> i > GenericQualifiers.QUALIFIER_MILESTONE);
    }

    /**
     * Filters any version that contains "pre-release" qualifiers (alpha, beta, milestone, rc/cr).
     */
    public static GenericQualifiersVersionFilter releasePreviewVersionFilter() {
        return new GenericQualifiersVersionFilter(i -> i > GenericQualifiers.QUALIFIER_RC);
    }

    /**
     * Filters any version that contains "non-final" qualifiers including snapshots (alpha, beta, milestone,
     * rc/cr, snapshot).
     */
    public static GenericQualifiersVersionFilter nonReleaseVersionFilter() {
        return new GenericQualifiersVersionFilter(i -> i > GenericQualifiers.QUALIFIER_SNAPSHOT);
    }


    /**
     * Filters any version that contains any qualifiers.
     */
    public static GenericQualifiersVersionFilter anyQualifierVersionFilter() {
        return new GenericQualifiersVersionFilter(i -> false);
    }

    /**
     * Constructor that is able to select which qualifier to accept. Passed in predicate is invoked for version with
     * detected qualifiers only, while versions without qualifiers are accepted.
     *
     * @see GenericQualifiers
     */
    public GenericQualifiersVersionFilter(Predicate<Integer> qualifierPredicate) {
        super(v -> GenericQualifiers.qualifier(v.toString())
                .map(qualifierPredicate::test)
                .orElse(true));
    }
}
