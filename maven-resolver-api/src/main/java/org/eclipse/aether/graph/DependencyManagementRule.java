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
package org.eclipse.aether.graph;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * Represents a dependency management rule, a managed value of given attribute.
 *
 * @since 2.0.13
 */
public class DependencyManagementRule<T> implements UnaryOperator<Dependency> {
    public enum Kind {
        VERSION, SCOPE, OPTIONAL, PROPERTIES, EXCLUSIONS
    }

    public static ManagedVersion managedVersion(String version, boolean enforcing) {
        return new ManagedVersion(version, enforcing);
    }

    public static ManagedScope managedScope(String scope, boolean enforcing) {
        return new ManagedScope(scope, enforcing);
    }

    public static ManagedOptional managedOptional(Boolean optional, boolean enforcing) {
        return new ManagedOptional(optional, enforcing);
    }

    public static ManagedProperties managedProperties(Map<String, String> properties, boolean enforcing) {
        return new ManagedProperties(properties, enforcing);
    }

    public static ManagedExclusions managedExclusions(Collection<Exclusion> exclusions, boolean enforcing) {
        return new ManagedExclusions(exclusions, enforcing);
    }

    private final T value;

    private final Kind kind;

    private final boolean enforcing;

    private final UnaryOperator<Dependency> operator;

    protected DependencyManagementRule(T value, Kind kind, boolean enforcing, UnaryOperator<Dependency> operator) {
        this.value = requireNonNull(value);
        this.kind = requireNonNull(kind);
        this.enforcing = enforcing;
        this.operator = requireNonNull(operator);
    }

    public T getValue() {
        return value;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isEnforcing() {
        return enforcing;
    }

    @Override
    public Dependency apply(Dependency dependency) {
        requireNonNull(dependency);
        return operator.apply(dependency);
    }

    public static final class ManagedVersion extends DependencyManagementRule<String> {
        private ManagedVersion(String value, boolean enforcing) {
            super(value, Kind.VERSION, enforcing, d -> d.setArtifact(d.getArtifact().setVersion(value)));
        }
    }

    public static final class ManagedScope extends DependencyManagementRule<String> {
        private ManagedScope(String value, boolean enforcing) {
            super(value, Kind.SCOPE, enforcing, d -> d.setScope(value));
        }
    }

    public static final class ManagedOptional extends DependencyManagementRule<Boolean> {
        private ManagedOptional(Boolean value, boolean enforcing) {
            super(value, Kind.OPTIONAL, enforcing, d -> d.setOptional(value));
        }
    }

    public static final class ManagedProperties extends DependencyManagementRule<Map<String, String>> {
        private ManagedProperties(Map<String, String> value, boolean enforcing) {
            super(value, Kind.PROPERTIES, enforcing, d -> {
                HashMap<String, String> properties = new HashMap<>(d.getArtifact().getProperties());
                properties.putAll(value);
                return d.setArtifact(d.getArtifact().setProperties(properties));
            });
        }
    }

    public static final class ManagedExclusions extends DependencyManagementRule<Collection<Exclusion>> {
        private ManagedExclusions(Collection<Exclusion> value, boolean enforcing) {
            super(value, Kind.EXCLUSIONS, enforcing, d -> d.setExclusions(value));
        }
    }
}
