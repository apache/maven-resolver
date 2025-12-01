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
package org.eclipse.aether.internal.impl.scope;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.impl.scope.BuildPath;
import org.eclipse.aether.impl.scope.BuildScope;
import org.eclipse.aether.impl.scope.BuildScopeQuery;
import org.eclipse.aether.impl.scope.BuildScopeSource;
import org.eclipse.aether.impl.scope.InternalScopeManager;
import org.eclipse.aether.impl.scope.ProjectPath;
import org.eclipse.aether.impl.scope.ScopeManagerConfiguration;
import org.eclipse.aether.scope.DependencyScope;
import org.eclipse.aether.scope.ResolutionScope;
import org.eclipse.aether.scope.SystemDependencyScope;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.visitor.CloningDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.FilteringDependencyVisitor;

import static java.util.Objects.requireNonNull;

public final class ScopeManagerImpl implements InternalScopeManager {
    private final String id;
    private final boolean strictDependencyScopes;
    private final boolean strictResolutionScopes;
    private final BuildScopeSource buildScopeSource;
    private final AtomicReference<SystemDependencyScopeImpl> systemDependencyScope;
    private final Map<String, DependencyScopeImpl> dependencyScopes;
    private final Collection<DependencyScope> dependencyScopesUniverse;
    private final Map<String, ResolutionScopeImpl> resolutionScopes;
    private final Collection<ResolutionScope> resolutionScopesUniverse;

    public ScopeManagerImpl(ScopeManagerConfiguration configuration) {
        this.id = configuration.getId();
        this.strictDependencyScopes = configuration.isStrictDependencyScopes();
        this.strictResolutionScopes = configuration.isStrictResolutionScopes();
        this.buildScopeSource = configuration.getBuildScopeSource();
        this.systemDependencyScope = new AtomicReference<>(null);
        this.dependencyScopes = Collections.unmodifiableMap(buildDependencyScopes(configuration));
        this.dependencyScopesUniverse = Collections.unmodifiableCollection(new HashSet<>(dependencyScopes.values()));
        this.resolutionScopes = Collections.unmodifiableMap(buildResolutionScopes(configuration));
        this.resolutionScopesUniverse = Collections.unmodifiableCollection(new HashSet<>(resolutionScopes.values()));
    }

    private Map<String, DependencyScopeImpl> buildDependencyScopes(ScopeManagerConfiguration configuration) {
        Collection<DependencyScope> dependencyScopes = configuration.buildDependencyScopes(this);
        HashMap<String, DependencyScopeImpl> result = new HashMap<>(dependencyScopes.size());
        dependencyScopes.forEach(d -> result.put(d.getId(), (DependencyScopeImpl) d));
        return result;
    }

    private Map<String, ResolutionScopeImpl> buildResolutionScopes(ScopeManagerConfiguration configuration) {
        Collection<ResolutionScope> resolutionScopes = configuration.buildResolutionScopes(this);
        HashMap<String, ResolutionScopeImpl> result = new HashMap<>(resolutionScopes.size());
        resolutionScopes.forEach(r -> result.put(r.getId(), (ResolutionScopeImpl) r));
        return result;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Optional<SystemDependencyScope> getSystemDependencyScope() {
        return Optional.ofNullable(systemDependencyScope.get());
    }

    @Override
    public Optional<DependencyScope> getDependencyScope(String id) {
        DependencyScope dependencyScope = dependencyScopes.get(id);
        if (strictDependencyScopes && dependencyScope == null) {
            throw new IllegalArgumentException("unknown dependency scope");
        }
        return Optional.ofNullable(dependencyScope);
    }

    @Override
    public Collection<DependencyScope> getDependencyScopeUniverse() {
        return dependencyScopesUniverse;
    }

    @Override
    public Optional<ResolutionScope> getResolutionScope(String id) {
        ResolutionScope resolutionScope = resolutionScopes.get(id);
        if (strictResolutionScopes && resolutionScope == null) {
            throw new IllegalArgumentException("unknown resolution scope");
        }
        return Optional.ofNullable(resolutionScope);
    }

    @Override
    public Collection<ResolutionScope> getResolutionScopeUniverse() {
        return resolutionScopesUniverse;
    }

    @Override
    public int getDependencyScopeWidth(DependencyScope dependencyScope) {
        return translate(dependencyScope).getWidth();
    }

    @Override
    public Optional<BuildScope> getDependencyScopeMainProjectBuildScope(DependencyScope dependencyScope) {
        return Optional.ofNullable(translate(dependencyScope).getMainBuildScope());
    }

    @Override
    public DependencySelector getDependencySelector(RepositorySystemSession session, ResolutionScope resolutionScope) {
        ResolutionScopeImpl rs = translate(resolutionScope);
        Set<String> directlyExcludedLabels = getDirectlyExcludedLabels(rs);
        Set<String> transitivelyExcludedLabels = getTransitivelyExcludedLabels(rs);
        if (session.getDependencySelector() != null) {
            return new AndDependencySelector(
                    rs.getMode() == Mode.ELIMINATE
                            ? ScopeDependencySelector.fromTo(2, 2, null, directlyExcludedLabels)
                            : ScopeDependencySelector.fromTo(1, 2, null, directlyExcludedLabels),
                    ScopeDependencySelector.from(2, null, transitivelyExcludedLabels),
                    OptionalDependencySelector.fromDirect(),
                    new ExclusionDependencySelector(),
                    session.getDependencySelector());
        } else {
            return new AndDependencySelector(
                    rs.getMode() == Mode.ELIMINATE
                            ? ScopeDependencySelector.fromTo(2, 2, null, directlyExcludedLabels)
                            : ScopeDependencySelector.fromTo(1, 2, null, directlyExcludedLabels),
                    ScopeDependencySelector.from(2, null, transitivelyExcludedLabels),
                    OptionalDependencySelector.fromDirect(),
                    new ExclusionDependencySelector());
        }
    }

    @Override
    public CollectResult postProcess(
            RepositorySystemSession session, ResolutionScope resolutionScope, CollectResult collectResult) {
        ResolutionScopeImpl rs = translate(resolutionScope);
        if (rs.getMode() == Mode.ELIMINATE) {
            CloningDependencyVisitor cloning = new CloningDependencyVisitor();
            FilteringDependencyVisitor filter = new FilteringDependencyVisitor(
                    cloning, new ScopeDependencyFilter(null, getDirectlyExcludedLabels(rs)));
            collectResult.getRoot().accept(filter);
            collectResult.setRoot(cloning.getRootNode());
        }
        return collectResult;
    }

    @Override
    public DependencyScope createDependencyScope(String id, boolean transitive, Collection<BuildScopeQuery> presence) {
        return new DependencyScopeImpl(id, transitive, presence);
    }

    @Override
    public SystemDependencyScope createSystemDependencyScope(
            String id, boolean transitive, Collection<BuildScopeQuery> presence, String systemPathProperty) {
        SystemDependencyScopeImpl system = new SystemDependencyScopeImpl(id, transitive, presence, systemPathProperty);
        if (systemDependencyScope.compareAndSet(null, system)) {
            return system;
        } else {
            throw new IllegalStateException("system dependency scope already created");
        }
    }

    @Override
    public ResolutionScope createResolutionScope(
            String id,
            Mode mode,
            Collection<BuildScopeQuery> wantedPresence,
            Collection<DependencyScope> explicitlyIncluded,
            Collection<DependencyScope> transitivelyExcluded) {
        return new ResolutionScopeImpl(id, mode, wantedPresence, explicitlyIncluded, transitivelyExcluded);
    }

    private Set<DependencyScope> collectScopes(Collection<BuildScopeQuery> wantedPresence) {
        HashSet<DependencyScope> result = new HashSet<>();
        for (BuildScope buildScope : buildScopeSource.query(wantedPresence)) {
            dependencyScopes.values().stream()
                    .filter(s -> buildScopeSource.query(s.getPresence()).contains(buildScope))
                    .filter(s -> systemDependencyScope.get() == null
                            || !systemDependencyScope.get().is(s.id)) // system scope must be always explicitly added
                    .forEach(result::add);
        }
        return result;
    }

    private int calculateDependencyScopeWidth(DependencyScopeImpl dependencyScope) {
        int result = 0;
        if (dependencyScope.isTransitive()) {
            result += 1000;
        }
        for (BuildScope buildScope : buildScopeSource.query(dependencyScope.getPresence())) {
            result += 1000
                    / buildScope.getProjectPaths().stream()
                            .map(ProjectPath::order)
                            .reduce(0, Integer::sum);
        }
        return result;
    }

    private BuildScope calculateMainProjectBuildScope(DependencyScopeImpl dependencyScope) {
        for (ProjectPath projectPath : buildScopeSource.allProjectPaths().stream()
                .sorted(Comparator.comparing(ProjectPath::order))
                .collect(Collectors.toList())) {
            for (BuildPath buildPath : buildScopeSource.allBuildPaths().stream()
                    .sorted(Comparator.comparing(BuildPath::order))
                    .collect(Collectors.toList())) {
                for (BuildScope buildScope : buildScopeSource.query(dependencyScope.getPresence())) {
                    if (buildScope.getProjectPaths().contains(projectPath)
                            && buildScope.getBuildPaths().contains(buildPath)) {
                        return buildScope;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Visible for testing.
     */
    Set<String> getDirectlyIncludedLabels(ResolutionScope resolutionScope) {
        return translate(resolutionScope).getDirectlyIncluded().stream()
                .map(DependencyScope::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Visible for testing.
     */
    Set<String> getDirectlyExcludedLabels(ResolutionScope resolutionScope) {
        ResolutionScopeImpl rs = translate(resolutionScope);
        return dependencyScopes.values().stream()
                .filter(s -> !rs.getDirectlyIncluded().contains(s))
                .map(DependencyScope::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Visible for testing.
     */
    Set<String> getTransitivelyExcludedLabels(ResolutionScope resolutionScope) {
        return translate(resolutionScope).getTransitivelyExcluded().stream()
                .map(DependencyScope::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Visible for testing.
     */
    Set<BuildScopeQuery> getPresence(DependencyScope dependencyScope) {
        return translate(dependencyScope).getPresence();
    }

    /**
     * Visible for testing.
     */
    BuildScopeSource getBuildScopeSource() {
        return buildScopeSource;
    }

    private DependencyScopeImpl translate(DependencyScope dependencyScope) {
        return requireNonNull(dependencyScopes.get(dependencyScope.getId()), "unknown dependency scope");
    }

    private ResolutionScopeImpl translate(ResolutionScope resolutionScope) {
        return requireNonNull(resolutionScopes.get(resolutionScope.getId()), "unknown resolution scope");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ScopeManagerImpl that = (ScopeManagerImpl) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }

    private class DependencyScopeImpl implements DependencyScope {
        private final String id;
        private final boolean transitive;
        private final Set<BuildScopeQuery> presence;
        private final BuildScope mainBuildScope;
        private final int width;

        private DependencyScopeImpl(String id, boolean transitive, Collection<BuildScopeQuery> presence) {
            this.id = requireNonNull(id, "id");
            this.transitive = transitive;
            this.presence = Collections.unmodifiableSet(new HashSet<>(presence));
            this.mainBuildScope = calculateMainProjectBuildScope(this);
            this.width = calculateDependencyScopeWidth(this);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isTransitive() {
            return transitive;
        }

        public Set<BuildScopeQuery> getPresence() {
            return presence;
        }

        public BuildScope getMainBuildScope() {
            return mainBuildScope;
        }

        public int getWidth() {
            return width;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DependencyScopeImpl that = (DependencyScopeImpl) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return id;
        }
    }

    private class SystemDependencyScopeImpl extends DependencyScopeImpl implements SystemDependencyScope {
        private final String systemPathProperty;

        private SystemDependencyScopeImpl(
                String id, boolean transitive, Collection<BuildScopeQuery> presence, String systemPathProperty) {
            super(id, transitive, presence);
            this.systemPathProperty = requireNonNull(systemPathProperty);
        }

        @Override
        public String getSystemPath(Artifact artifact) {
            return artifact.getProperty(systemPathProperty, null);
        }

        @Override
        public void setSystemPath(Map<String, String> properties, String systemPath) {
            if (systemPath == null) {
                properties.remove(systemPathProperty);
            } else {
                properties.put(systemPathProperty, systemPath);
            }
        }
    }

    private class ResolutionScopeImpl implements ResolutionScope {
        private final String id;
        private final Mode mode;
        private final Set<BuildScopeQuery> wantedPresence;
        private final Set<DependencyScope> directlyIncluded;
        private final Set<DependencyScope> transitivelyExcluded;

        private ResolutionScopeImpl(
                String id,
                Mode mode,
                Collection<BuildScopeQuery> wantedPresence,
                Collection<DependencyScope> explicitlyIncluded,
                Collection<DependencyScope> transitivelyExcluded) {
            this.id = requireNonNull(id, "id");
            this.mode = requireNonNull(mode, "mode");
            this.wantedPresence = Collections.unmodifiableSet(new HashSet<>(wantedPresence));
            Set<DependencyScope> included = collectScopes(wantedPresence);
            // here we may have null elements, based on existence of system scope
            if (explicitlyIncluded != null && !explicitlyIncluded.isEmpty()) {
                explicitlyIncluded.stream().filter(Objects::nonNull).forEach(included::add);
            }
            this.directlyIncluded = Collections.unmodifiableSet(included);
            this.transitivelyExcluded = Collections.unmodifiableSet(
                    transitivelyExcluded.stream().filter(Objects::nonNull).collect(Collectors.toSet()));
        }

        @Override
        public String getId() {
            return id;
        }

        public Mode getMode() {
            return mode;
        }

        public Set<BuildScopeQuery> getWantedPresence() {
            return wantedPresence;
        }

        public Set<DependencyScope> getDirectlyIncluded() {
            return directlyIncluded;
        }

        public Set<DependencyScope> getTransitivelyExcluded() {
            return transitivelyExcluded;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ResolutionScopeImpl that = (ResolutionScopeImpl) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return id;
        }
    }
}
