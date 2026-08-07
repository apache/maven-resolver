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

import java.util.*;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.internal.impl.StubRemoteRepositoryManager;
import org.eclipse.aether.internal.impl.StubVersionRangeResolver;
import org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegate;
import org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegateTestSupport;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.eclipse.aether.util.graph.manager.TransitiveDependencyManager;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT for {@link BfDependencyCollector}.
 */
public class BfWithSkipperDependencyCollectorTest extends DependencyCollectorDelegateTestSupport {
    @Override
    protected DependencyCollectorDelegate setupCollector(ArtifactDescriptorReader artifactDescriptorReader) {
        session.setConfigProperty(BfDependencyCollector.CONFIG_PROP_SKIPPER, true);

        return new BfDependencyCollector(
                new StubRemoteRepositoryManager(),
                artifactDescriptorReader,
                new StubVersionRangeResolver(),
                Collections.emptyMap());
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

    /**
     * Verifies that the pool cache is transparent w.r.t. the DependencyManager: the graph
     * structure must not depend on whether the pool hits or misses.
     * <p>
     * Scenario (from <a href="https://github.com/apache/maven-resolver/issues/2013">#2013</a>):
     * <pre>
     *   root
     *   ├── b      → c → d
     *   └── b-alt  → c → d   (c is a shared transitive dependency)
     * </pre>
     * With {@link TransitiveDependencyManager}, {@code deriveChildManager()} used to always
     * create a new instance (unique {@code path} field), making every pool key unique.
     * The pool would miss for {@code c} under {@code b-alt}, the skipper would mark it as
     * a duplicate, and the node would end up with zero children — even though the same
     * {@code c} under {@code b} had children.
     * <p>
     * The fix in {@code AbstractDependencyManager.deriveChildManager()} reuses the same
     * manager instance when no new management data is collected, so the pool key matches
     * and children are preserved.
     */
    @Test
    void testPoolCacheTransparencyWithTransitiveDependencyManager() throws DependencyCollectionException {
        collector = setupCollector(newReader("pool-cache-transparency/"));
        parser = new DependencyGraphParser("artifact-descriptions/pool-cache-transparency/");
        session.setDependencyManager(new TransitiveDependencyManager(null));

        Dependency root = newDep("gid:root:ext:1.0", "compile");
        CollectRequest request = new CollectRequest(root, Collections.singletonList(repository));
        CollectResult result = collector.collectDependencies(session, request);

        assertEquals(0, result.getExceptions().size());

        // root has two children: b and b-alt
        DependencyNode rootNode = result.getRoot();
        assertEquals(2, rootNode.getChildren().size(), "root should have 2 children (b, b-alt)");

        // b → c
        DependencyNode b = rootNode.getChildren().get(0);
        assertEquals("b", b.getArtifact().getArtifactId());
        assertFalse(b.getChildren().isEmpty(), "b should have children");

        // b → c → d
        DependencyNode cUnderB = b.getChildren().get(0);
        assertEquals("c", cUnderB.getArtifact().getArtifactId());
        assertFalse(cUnderB.getChildren().isEmpty(), "c under b should have children (d)");

        // b-alt → c  (this is the key assertion: c under b-alt must also have children)
        DependencyNode bAlt = rootNode.getChildren().get(1);
        assertEquals("b-alt", bAlt.getArtifact().getArtifactId());
        assertFalse(bAlt.getChildren().isEmpty(), "b-alt should have children");

        DependencyNode cUnderBAlt = bAlt.getChildren().get(0);
        assertEquals("c", cUnderBAlt.getArtifact().getArtifactId());

        // Before the fix, this assertion would fail: c under b-alt had zero children
        // because the pool key differed (different DependencyManager instance) and the
        // skipper marked it as a duplicate.
        assertFalse(
                cUnderBAlt.getChildren().isEmpty(),
                "c under b-alt should have children (d) — pool cache must be transparent");

        // Verify that c's child is d in both subtrees
        assertEquals("d", cUnderB.getChildren().get(0).getArtifact().getArtifactId());
        assertTrue(
                cUnderBAlt.getChildren().stream()
                        .anyMatch(n -> "d".equals(n.getArtifact().getArtifactId())),
                "c under b-alt should have d as a child");
    }

    @Test
    void testSkipperWithDifferentExclusion() throws DependencyCollectionException {
        collector = setupCollector(newReader("managed/"));
        parser = new DependencyGraphParser("artifact-descriptions/managed/");
        session.setDependencyManager(new TransitiveDependencyManager(null));

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
