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
package org.eclipse.aether.internal.impl.collect;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.impl.DependencyCollector;
import org.eclipse.aether.util.ConfigUtils;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link DependencyCollector} that merely indirect to selected delegate.
 */
@Singleton
@Named
public class DefaultDependencyCollector implements DependencyCollector {

    public static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_AETHER + "dependencyCollector.";

    /**
     * The name of the dependency collector implementation to use: depth-first (original) named "df", and
     * breadth-first (new in 1.8.0) named "bf". Both collectors produce equivalent results, but they may differ
     * performance wise, depending on project being applied to. Our experience shows that existing "df" is well
     * suited for smaller to medium size projects, while "bf" may perform better on huge projects with many
     * dependencies. Experiment (and come back to us!) to figure out which one suits you the better.
     *
     * @since 1.8.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_COLLECTOR_IMPL}
     */
    public static final String CONFIG_PROP_COLLECTOR_IMPL = CONFIG_PROPS_PREFIX + "impl";

    public static final String DEFAULT_COLLECTOR_IMPL =
            org.eclipse.aether.internal.impl.collect.bf.BfDependencyCollector.NAME;

    private final Map<String, DependencyCollectorDelegate> delegates;

    @Inject
    public DefaultDependencyCollector(Map<String, DependencyCollectorDelegate> delegates) {
        this.delegates = requireNonNull(delegates);
    }

    @Override
    public CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)
            throws DependencyCollectionException {
        String delegateName = ConfigUtils.getString(session, DEFAULT_COLLECTOR_IMPL, CONFIG_PROP_COLLECTOR_IMPL);
        DependencyCollectorDelegate delegate = delegates.get(delegateName);
        if (delegate == null) {
            throw new IllegalArgumentException(
                    "Unknown collector impl: '" + delegateName + "', known implementations are " + delegates.keySet());
        }
        return delegate.collectDependencies(session, request);
    }
}
