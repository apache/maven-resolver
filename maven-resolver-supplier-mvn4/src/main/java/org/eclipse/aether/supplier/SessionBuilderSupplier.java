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
package org.eclipse.aether.supplier;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.maven.api.DependencyScope;
import org.apache.maven.repository.internal.artifact.FatArtifactTraverser;
import org.apache.maven.repository.internal.scopes.Maven4ScopeManagerConfiguration;
import org.apache.maven.repository.internal.type.DefaultTypeProvider;
import org.apache.maven.utils.Os;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.RepositorySystemSession.SessionBuilder;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.impl.scope.InternalScopeManager;
import org.eclipse.aether.internal.impl.scope.ManagedDependencyContextRefiner;
import org.eclipse.aether.internal.impl.scope.ManagedScopeDeriver;
import org.eclipse.aether.internal.impl.scope.ManagedScopeSelector;
import org.eclipse.aether.internal.impl.scope.OptionalDependencySelector;
import org.eclipse.aether.internal.impl.scope.ScopeDependencySelector;
import org.eclipse.aether.internal.impl.scope.ScopeManagerImpl;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;
import org.eclipse.aether.util.artifact.DefaultArtifactTypeRegistry;
import org.eclipse.aether.util.graph.manager.ClassicDependencyManager;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.NearestVersionSelector;
import org.eclipse.aether.util.graph.transformer.PathConflictResolver;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;

/**
 * A simple {@link Supplier} of {@link SessionBuilder} instances, that on each call supplies newly
 * constructed instance. To create session out of builder, use {@link SessionBuilder#build()}. For proper closing
 * of sessions, use {@link CloseableSession#close()} method on built instance(s).
 * <p>
 * Extend this class and override methods to customize, if needed.
 *
 * @since 2.0.0
 */
public class SessionBuilderSupplier {
    protected final RepositorySystem repositorySystem;
    protected final InternalScopeManager scopeManager;

    public SessionBuilderSupplier(RepositorySystem repositorySystem) {
        this.repositorySystem = (RepositorySystem) Objects.requireNonNull(repositorySystem);
        this.scopeManager = new ScopeManagerImpl(Maven4ScopeManagerConfiguration.INSTANCE);
    }

    /** @deprecated */
    @Deprecated
    public SessionBuilderSupplier() {
        this.repositorySystem = null;
        this.scopeManager = new ScopeManagerImpl(Maven4ScopeManagerConfiguration.INSTANCE);
    }

    protected InternalScopeManager getScopeManager() {
        return this.scopeManager;
    }

    protected DependencyTraverser getDependencyTraverser() {
        return new FatArtifactTraverser();
    }

    protected DependencyManager getDependencyManager() {
        return this.getDependencyManager(true);
    }

    public DependencyManager getDependencyManager(boolean transitive) {
        return new ClassicDependencyManager(transitive, this.getScopeManager());
    }

    protected DependencySelector getDependencySelector() {
        return new AndDependencySelector(new DependencySelector[] {
            ScopeDependencySelector.legacy(
                    (Collection) null, Arrays.asList(DependencyScope.TEST.id(), DependencyScope.PROVIDED.id())),
            OptionalDependencySelector.fromDirect(),
            new ExclusionDependencySelector()
        });
    }

    protected DependencyGraphTransformer getDependencyGraphTransformer() {
        return new ChainedDependencyGraphTransformer(new DependencyGraphTransformer[] {
            new PathConflictResolver(
                    new NearestVersionSelector(),
                    new ManagedScopeSelector(this.getScopeManager()),
                    new SimpleOptionalitySelector(),
                    new ManagedScopeDeriver(this.getScopeManager())),
            new ManagedDependencyContextRefiner(this.getScopeManager())
        });
    }

    protected ArtifactTypeRegistry getArtifactTypeRegistry() {
        DefaultArtifactTypeRegistry stereotypes = new DefaultArtifactTypeRegistry();
        new DefaultTypeProvider().types().forEach(stereotypes::add);
        return stereotypes;
    }

    protected ArtifactDescriptorPolicy getArtifactDescriptorPolicy() {
        return new SimpleArtifactDescriptorPolicy(true, true);
    }

    protected void configureSessionBuilder(RepositorySystemSession.SessionBuilder session) {
        session.setDependencyTraverser(this.getDependencyTraverser());
        session.setDependencyManager(this.getDependencyManager());
        session.setDependencySelector(this.getDependencySelector());
        session.setDependencyGraphTransformer(this.getDependencyGraphTransformer());
        session.setArtifactTypeRegistry(this.getArtifactTypeRegistry());
        session.setArtifactDescriptorPolicy(this.getArtifactDescriptorPolicy());
        session.setScopeManager(this.getScopeManager());

        session.setSystemProperties(System.getProperties());
        boolean caseSensitive = !Os.IS_WINDOWS;
        System.getenv().forEach((key, value) -> {
            key = "env." + (caseSensitive ? key : key.toUpperCase(Locale.ENGLISH));
            session.setSystemProperty(key, value);
        });
    }

    public RepositorySystemSession.SessionBuilder get() {
        Objects.requireNonNull(this.repositorySystem, "repositorySystem");
        RepositorySystemSession.SessionBuilder builder = this.repositorySystem.createSessionBuilder();
        this.configureSessionBuilder(builder);
        return builder;
    }
}
