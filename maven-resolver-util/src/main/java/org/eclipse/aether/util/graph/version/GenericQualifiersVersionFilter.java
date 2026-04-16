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

import org.eclipse.aether.util.version.GenericQualifiers;

import java.util.function.Predicate;

/**
 * A version filter that (unconditionally) blocks based on qualifiers, as defined by {@link GenericQualifiers}.
 *
 * @since 2.0.17
 */
public class GenericQualifiersVersionFilter extends VersionPredicateVersionFilter {
    /**
     * Filters any version that contains "preview" qualifiers.
     */
    public static GenericQualifiersVersionFilter previewVersionFilter() {
        return new GenericQualifiersVersionFilter(i -> i >= GenericQualifiers.QUALIFIER_SNAPSHOT);
    }

    /**
     * Filters any version that contains any qualifiers.
     */
    public static GenericQualifiersVersionFilter anyQualifierVersionFilter() {
        return new GenericQualifiersVersionFilter(i -> false);
    }

    private GenericQualifiersVersionFilter(Predicate<Integer> qualifierPredicate) {
        super(v -> GenericQualifiers.qualifier(v.toString())
                .map(qualifierPredicate::test)
                .orElse(true));
    }

}
