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
package org.eclipse.aether.internal.impl.collect.bf;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.internal.impl.StubRemoteRepositoryManager;
import org.eclipse.aether.internal.impl.StubVersionRangeResolver;
import org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegate;
import org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegateTestSupport;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.eclipse.aether.util.graph.manager.TransitiveDependencyManager;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;

/**
 * UT for {@link BfDependencyCollector}.
 */
@RunWith(Parameterized.class)
public class BfDependencyCollectorTest extends DependencyCollectorDelegateTestSupport {
    @Parameterized.Parameters
    public static List<Boolean> parameters() {
        return Arrays.asList(Boolean.TRUE, Boolean.FALSE);
    }

    @Parameterized.Parameter
    public boolean useSkipper;

    @Override
    protected DependencyCollectorDelegate setupCollector(ArtifactDescriptorReader artifactDescriptorReader) {
        session.setConfigProperty(BfDependencyCollector.CONFIG_PROP_SKIPPER, useSkipper);

        return new BfDependencyCollector(
                new StubRemoteRepositoryManager(), artifactDescriptorReader, new StubVersionRangeResolver());
    }

    @Override
    protected String getTransitiveDepsUseRangesDirtyTreeResource() {
        return "transitiveDepsUseRangesDirtyTreeResult_BF.txt";
    }

    @Override
    protected String getTransitiveDepsUseRangesAndRelocationDirtyTreeResource() {
        return "transitiveDepsUseRangesAndRelocationDirtyTreeResult_BF.txt";
    }

    private Dependency newDep(String coords, String scope, Collection<Exclusion> exclusions) {
        Dependency d = new Dependency(new DefaultArtifact(coords), scope);
        return d.setExclusions(exclusions);
    }

    @Test
    public void testSkipperWithDifferentExclusion() throws DependencyCollectionException {
        Assume.assumeTrue(useSkipper);
        collector = setupCollector(newReader("managed/"));
        parser = new DependencyGraphParser("artifact-descriptions/managed/");
        session.setDependencyManager(new TransitiveDependencyManager());

        ExclusionDependencySelector exclSel1 = new ExclusionDependencySelector();
        session.setDependencySelector(exclSel1);

        Dependency root1 = newDep(
                "gid:root:ext:ver", "compile", Collections.singleton(new Exclusion("gid", "transitive-1", "", "ext")));
        Dependency root2 = newDep(
                "gid:root:ext:ver", "compile", Collections.singleton(new Exclusion("gid", "transitive-2", "", "ext")));
        List<Dependency> dependencies = Arrays.asList(root1, root2);

        CollectRequest request = new CollectRequest(dependencies, null, Collections.singletonList(repository));
        request.addManagedDependency(newDep("gid:direct:ext:managed-by-dominant-request"));
        request.addManagedDependency(newDep("gid:transitive-1:ext:managed-by-root"));

        CollectResult result = collector.collectDependencies(session, request);
        assertEquals(0, result.getExceptions().size());
        assertEquals(2, result.getRoot().getChildren().size());
        assertEquals(root1, dep(result.getRoot(), 0));
        assertEquals(root2, dep(result.getRoot(), 1));
        // the winner has transitive-1 excluded
        assertEquals(1, path(result.getRoot(), 0).getChildren().size());
        assertEquals(0, path(result.getRoot(), 0, 0).getChildren().size());
        // skipped
        assertEquals(0, path(result.getRoot(), 1).getChildren().size());
    }
}
