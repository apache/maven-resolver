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
import java.util.Objects;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * Represents a dependency management rule, a managed value of given {@link DependencyManagementSubject}.
 *
 * @param <T> the type of subject rule operates on.
 * @since 2.0.13
 */
public class DependencyManagementRule<T> implements UnaryOperator<Dependency> {
    public static ManagedVersion managedVersion(String version, boolean enforcing) {
        return new ManagedVersion(version, enforcing);
    }

    public static ManagedScope managedScope(String scope, boolean enforcing) {
        return new ManagedScope(scope, enforcing);
    }

    public static ManagedOptional managedOptional(Boolean optional, boolean enforcing) {
        return new ManagedOptional(optional, enforcing);
    }

    public static ManagedPropertiesPut managedPropertiesPut(Map<String, String> properties, boolean enforcing) {
        return new ManagedPropertiesPut(properties, enforcing);
    }

    public static ManagedPropertiesRemove managedPropertiesRemove(Collection<String> keys, boolean enforcing) {
        return new ManagedPropertiesRemove(keys, enforcing);
    }

    public static ManagedExclusions managedExclusions(Collection<Exclusion> exclusions, boolean enforcing) {
        return new ManagedExclusions(exclusions, enforcing);
    }

    private final T value;

    private final DependencyManagementSubject subject;

    private final boolean enforcing;

    private final UnaryOperator<Dependency> operator;

    private final int hashCode;

    protected DependencyManagementRule(
            T value, DependencyManagementSubject subject, boolean enforcing, UnaryOperator<Dependency> operator) {
        this.value = requireNonNull(value);
        this.subject = requireNonNull(subject);
        this.enforcing = enforcing;
        this.operator = requireNonNull(operator);

        this.hashCode = Objects.hash(value, subject, enforcing);
    }

    public T getValue() {
        return value;
    }

    public DependencyManagementSubject getSubject() {
        return subject;
    }

    public boolean isEnforcing() {
        return enforcing;
    }

    @Override
    public Dependency apply(Dependency dependency) {
        requireNonNull(dependency);
        return operator.apply(dependency);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DependencyManagementRule)) {
            return false;
        }
        DependencyManagementRule<?> that = (DependencyManagementRule<?>) o;
        return enforcing == that.enforcing && Objects.equals(value, that.value) && subject == that.subject;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public static final class ManagedVersion extends DependencyManagementRule<String> {
        private ManagedVersion(String value, boolean enforcing) {
            super(
                    value,
                    DependencyManagementSubject.VERSION,
                    enforcing,
                    d -> d.setArtifact(d.getArtifact().setVersion(value)));
        }
    }

    public static final class ManagedScope extends DependencyManagementRule<String> {
        private ManagedScope(String value, boolean enforcing) {
            super(value, DependencyManagementSubject.SCOPE, enforcing, d -> d.setScope(value));
        }
    }

    public static final class ManagedOptional extends DependencyManagementRule<Boolean> {
        private ManagedOptional(Boolean value, boolean enforcing) {
            super(value, DependencyManagementSubject.OPTIONAL, enforcing, d -> d.setOptional(value));
        }
    }

    public static final class ManagedPropertiesPut extends DependencyManagementRule<Map<String, String>> {
        private ManagedPropertiesPut(Map<String, String> value, boolean enforcing) {
            super(value, DependencyManagementSubject.PROPERTIES, enforcing, d -> {
                HashMap<String, String> properties =
                        new HashMap<>(d.getArtifact().getProperties());
                properties.putAll(value);
                return d.setArtifact(d.getArtifact().setProperties(properties));
            });
        }
    }

    public static final class ManagedPropertiesRemove extends DependencyManagementRule<Collection<String>> {
        private ManagedPropertiesRemove(Collection<String> value, boolean enforcing) {
            super(value, DependencyManagementSubject.PROPERTIES, enforcing, d -> {
                HashMap<String, String> properties =
                        new HashMap<>(d.getArtifact().getProperties());
                value.forEach(properties::remove);
                return d.setArtifact(d.getArtifact().setProperties(properties));
            });
        }
    }

    public static final class ManagedExclusions extends DependencyManagementRule<Collection<Exclusion>> {
        private ManagedExclusions(Collection<Exclusion> value, boolean enforcing) {
            super(value, DependencyManagementSubject.EXCLUSIONS, enforcing, d -> d.setExclusions(value));
        }
    }
}
