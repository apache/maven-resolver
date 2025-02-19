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

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyCycle;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.internal.impl.IniArtifactDescriptorReader;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.graph.manager.ClassicDependencyManager;
import org.eclipse.aether.util.graph.manager.DefaultDependencyManager;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.manager.TransitiveDependencyManager;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.JavaScopeSelector;
import org.eclipse.aether.util.graph.transformer.NearestVersionSelector;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;
import org.eclipse.aether.util.graph.version.HighestVersionFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Common tests for various {@link DependencyCollectorDelegate} implementations.
 */
public abstract class DependencyCollectorDelegateTestSupport {

    protected DefaultRepositorySystemSession session;

    protected DependencyGraphParser parser;

    protected RemoteRepository repository;

    protected DependencyCollectorDelegate collector;

    protected IniArtifactDescriptorReader newReader(String prefix) {
        return new IniArtifactDescriptorReader("artifact-descriptions/" + prefix);
    }

    protected Dependency newDep(String coords) {
        return newDep(coords, "");
    }

    protected Dependency newDep(String coords, String scope) {
        return new Dependency(new DefaultArtifact(coords), scope);
    }

    @BeforeEach
    void setup() {
        session = TestUtils.newSession();
        parser = new DependencyGraphParser("artifact-descriptions/");
        repository = new RemoteRepository.Builder("id", "default", "file:///").build();
        collector = setupCollector(newReader(""));
    }

    protected abstract DependencyCollectorDelegate setupCollector(ArtifactDescriptorReader artifactDescriptorReader);

    private static void assertEqualSubtree(DependencyNode expected, DependencyNode actual) {
        assertEqualSubtree(expected, actual, new LinkedList<>());
    }

    private static void assertEqualSubtree(
            DependencyNode expected, DependencyNode actual, LinkedList<DependencyNode> parents) {
        assertEquals(expected.getDependency(), actual.getDependency(), "path: " + parents);

        if (actual.getDependency() != null) {
            Artifact artifact = actual.getDependency().getArtifact();
            for (DependencyNode parent : parents) {
                if (parent.getDependency() != null
                        && artifact.equals(parent.getDependency().getArtifact())) {
                    return;
                }
            }
        }

        parents.addLast(expected);

        assertEquals(
                expected.getChildren().size(),
                actual.getChildren().size(),
                "path: " + parents + ", expected: " + expected.getChildren() + ", actual: " + actual.getChildren());

        Iterator<DependencyNode> iterator1 = expected.getChildren().iterator();
        Iterator<DependencyNode> iterator2 = actual.getChildren().iterator();

        while (iterator1.hasNext()) {
            assertEqualSubtree(iterator1.next(), iterator2.next(), parents);
        }

        parents.removeLast();
    }

    protected Dependency dep(DependencyNode root, int... coords) {
        return path(root, coords).getDependency();
    }

    protected DependencyNode path(DependencyNode root, int... coords) {
        try {
            DependencyNode node = root;
            for (int coord : coords) {
                node = node.getChildren().get(coord);
            }

            return node;
        } catch (IndexOutOfBoundsException | NullPointerException e) {
            throw new IllegalArgumentException("illegal coordinates for child", e);
        }
    }

    @Test
    void testInterruption() throws Exception {
        Dependency dependency = newDep("gid:aid:ext:ver", "compile");
        CollectRequest request = new CollectRequest(dependency, singletonList(repository));
        AtomicReference<Object> cause = new AtomicReference<>(null);
        Thread t = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                collector.collectDependencies(session, request);
                fail("We should throw");
            } catch (DependencyCollectionException e) {
                cause.set(e.getCause());
            }
        });
        t.start();
        t.join();
        assertTrue(cause.get() instanceof InterruptedException, String.valueOf(cause.get()));
    }

    @Test
    void testSimpleCollection() throws DependencyCollectionException {
        Dependency dependency = newDep("gid:aid:ext:ver", "compile");
        CollectRequest request = new CollectRequest(dependency, singletonList(repository));
        CollectResult result = collector.collectDependencies(session, request);

        assertEquals(0, result.getExceptions().size());

        DependencyNode root = result.getRoot();
        Dependency newDependency = root.getDependency();

        assertEquals(dependency, newDependency);
        assertEquals(dependency.getArtifact(), newDependency.getArtifact());

        assertEquals(1, root.getChildren().size());

        Dependency expect = newDep("gid:aid2:ext:ver", "compile");
        assertEquals(expect, root.getChildren().get(0).getDependency());
    }

    @Test
    void testMissingDependencyDescription() {
        CollectRequest request = new CollectRequest(newDep("missing:description:ext:ver"), singletonList(repository));
        try {
            collector.collectDependencies(session, request);
            fail("expected exception");
        } catch (DependencyCollectionException e) {
            CollectResult result = e.getResult();
            assertSame(request, result.getRequest());
            assertNotNull(result.getExceptions());
            assertEquals(1, result.getExceptions().size());

            assertTrue(result.getExceptions().get(0) instanceof ArtifactDescriptorException);

            assertEquals(request.getRoot(), result.getRoot().getDependency());
        }
    }

    @Test
    void testDuplicates() throws DependencyCollectionException {
        Dependency dependency = newDep("duplicate:transitive:ext:dependency");
        CollectRequest request = new CollectRequest(dependency, singletonList(repository));

        CollectResult result = collector.collectDependencies(session, request);

        assertEquals(0, result.getExceptions().size());

        DependencyNode root = result.getRoot();
        Dependency newDependency = root.getDependency();

        assertEquals(dependency, newDependency);
        assertEquals(dependency.getArtifact(), newDependency.getArtifact());

        assertEquals(2, root.getChildren().size());

        Dependency dep = newDep("gid:aid:ext:ver", "compile");
        assertEquals(dep, dep(root, 0));

        dep = newDep("gid:aid2:ext:ver", "compile");
        assertEquals(dep, dep(root, 1));
        assertEquals(dep, dep(root, 0, 0));
        assertEquals(dep(root, 1), dep(root, 0, 0));
    }

    @Test
    void testEqualSubtree() throws IOException, DependencyCollectionException {
        DependencyNode root = parser.parseResource("expectedSubtreeComparisonResult.txt");
        Dependency dependency = root.getDependency();
        CollectRequest request = new CollectRequest(dependency, singletonList(repository));

        CollectResult result = collector.collectDependencies(session, request);
        assertEqualSubtree(root, result.getRoot());
    }

    @Test
    void testCyclicDependencies() throws Exception {
        DependencyNode root = parser.parseResource("cycle.txt");
        CollectRequest request = new CollectRequest(root.getDependency(), singletonList(repository));
        CollectResult result = collector.collectDependencies(session, request);
        assertEqualSubtree(root, result.getRoot());
    }

    @Test
    void testCyclicDependenciesBig() throws Exception {
        CollectRequest request = new CollectRequest(newDep("1:2:pom:5.50-SNAPSHOT"), singletonList(repository));
        collector = setupCollector(newReader("cycle-big/"));
        CollectResult result = collector.collectDependencies(session, request);
        assertNotNull(result.getRoot());
        // we only care about the performance here, this test must not hang or run out of mem
    }

    @Test
    void testCyclicProjects() throws Exception {
        CollectRequest request = new CollectRequest(newDep("test:a:2"), singletonList(repository));
        collector = setupCollector(newReader("versionless-cycle/"));
        CollectResult result = collector.collectDependencies(session, request);
        DependencyNode root = result.getRoot();
        DependencyNode a1 = path(root, 0, 0);
        assertEquals("a", a1.getArtifact().getArtifactId());
        assertEquals("1", a1.getArtifact().getVersion());
        for (DependencyNode child : a1.getChildren()) {
            assertNotEquals("1", child.getArtifact().getVersion());
        }

        assertEquals(1, result.getCycles().size());
        DependencyCycle cycle = result.getCycles().get(0);
        assertEquals(Collections.emptyList(), cycle.getPrecedingDependencies());
        assertEquals(
                Arrays.asList(root.getDependency(), path(root, 0).getDependency(), a1.getDependency()),
                cycle.getCyclicDependencies());
    }

    @Test
    void testCyclicProjects_ConsiderLabelOfRootlessGraph() throws Exception {
        Dependency dep = newDep("gid:aid:ver", "compile");
        CollectRequest request = new CollectRequest()
                .addDependency(dep)
                .addRepository(repository)
                .setRootArtifact(dep.getArtifact());
        CollectResult result = collector.collectDependencies(session, request);
        DependencyNode root = result.getRoot();
        DependencyNode a1 = root.getChildren().get(0);
        assertEquals("aid", a1.getArtifact().getArtifactId());
        assertEquals("ver", a1.getArtifact().getVersion());
        DependencyNode a2 = a1.getChildren().get(0);
        assertEquals("aid2", a2.getArtifact().getArtifactId());
        assertEquals("ver", a2.getArtifact().getVersion());

        assertEquals(1, result.getCycles().size());
        DependencyCycle cycle = result.getCycles().get(0);
        assertEquals(Collections.emptyList(), cycle.getPrecedingDependencies());
        assertEquals(
                Arrays.asList(new Dependency(dep.getArtifact(), null), a1.getDependency()),
                cycle.getCyclicDependencies());
    }

    @Test
    void testPartialResultOnError() throws IOException {
        DependencyNode root = parser.parseResource("expectedPartialSubtreeOnError.txt");

        Dependency dependency = root.getDependency();
        CollectRequest request = new CollectRequest(dependency, singletonList(repository));

        CollectResult result;
        try {
            collector.collectDependencies(session, request);
            fail("expected exception ");
        } catch (DependencyCollectionException e) {
            result = e.getResult();

            assertSame(request, result.getRequest());
            assertNotNull(result.getExceptions());
            assertEquals(1, result.getExceptions().size());

            assertTrue(result.getExceptions().get(0) instanceof ArtifactDescriptorException);

            assertEqualSubtree(root, result.getRoot());
        }
    }

    @Test
    void testCollectMultipleDependencies() throws DependencyCollectionException {
        Dependency root1 = newDep("gid:aid:ext:ver", "compile");
        Dependency root2 = newDep("gid:aid2:ext:ver", "compile");
        List<Dependency> dependencies = Arrays.asList(root1, root2);
        CollectRequest request = new CollectRequest(dependencies, null, singletonList(repository));
        CollectResult result = collector.collectDependencies(session, request);

        assertEquals(0, result.getExceptions().size());
        assertEquals(2, result.getRoot().getChildren().size());
        assertEquals(root1, dep(result.getRoot(), 0));

        assertEquals(1, path(result.getRoot(), 0).getChildren().size());
        assertEquals(root2, dep(result.getRoot(), 0, 0));

        assertEquals(0, path(result.getRoot(), 1).getChildren().size());
        assertEquals(root2, dep(result.getRoot(), 1));
    }

    @Test
    void testArtifactDescriptorResolutionNotRestrictedToRepoHostingSelectedVersion() throws Exception {
        RemoteRepository repo2 = new RemoteRepository.Builder("test", "default", "file:///").build();

        final List<RemoteRepository> repos = new ArrayList<>();

        collector = setupCollector(new ArtifactDescriptorReader() {
            @Override
            public ArtifactDescriptorResult readArtifactDescriptor(
                    RepositorySystemSession session, ArtifactDescriptorRequest request) {
                repos.addAll(request.getRepositories());
                return new ArtifactDescriptorResult(request);
            }
        });

        List<Dependency> dependencies = singletonList(newDep("verrange:parent:jar:1[1,)", "compile"));
        CollectRequest request = new CollectRequest(dependencies, null, Arrays.asList(repository, repo2));
        CollectResult result = collector.collectDependencies(session, request);

        assertEquals(0, result.getExceptions().size());
        assertEquals(2, repos.size());
        assertEquals("id", repos.get(0).getId());
        assertEquals("test", repos.get(1).getId());
    }

    @Test
    void testManagedVersionScope() throws DependencyCollectionException {
        Dependency dependency = newDep("managed:aid:ext:ver");
        CollectRequest request = new CollectRequest(dependency, singletonList(repository));

        session.setDependencyManager(new ClassicDependencyManager(null));

        CollectResult result = collector.collectDependencies(session, request);

        assertEquals(0, result.getExceptions().size());

        DependencyNode root = result.getRoot();

        assertEquals(dependency, dep(root));
        assertEquals(dependency.getArtifact(), dep(root).getArtifact());

        assertEquals(1, root.getChildren().size());
        Dependency expect = newDep("gid:aid:ext:ver", "compile");
        assertEquals(expect, dep(root, 0));

        assertEquals(1, path(root, 0).getChildren().size());
        expect = newDep("gid:aid2:ext:managedVersion", "managedScope");
        assertEquals(expect, dep(root, 0, 0));
    }

    @Test
    void testDependencyManagement() throws IOException, DependencyCollectionException {
        collector = setupCollector(newReader("managed/"));

        DependencyNode root = parser.parseResource("expectedSubtreeComparisonResult.txt");
        TestDependencyManager depMgmt = new TestDependencyManager();
        depMgmt.add(dep(root, 0), "managed", null, null);
        depMgmt.add(dep(root, 0, 1), "managed", "managed", null);
        depMgmt.add(dep(root, 1), null, null, "managed");
        session.setDependencyManager(depMgmt);

        // collect result will differ from expectedSubtreeComparisonResult.txt
        // set localPath -> no dependency traversal
        CollectRequest request = new CollectRequest(dep(root), singletonList(repository));
        CollectResult result = collector.collectDependencies(session, request);

        DependencyNode node = result.getRoot();
        assertEquals("managed", dep(node, 0, 1).getArtifact().getVersion());
        assertEquals("managed", dep(node, 0, 1).getScope());

        assertEquals("managed", dep(node, 1).getArtifact().getProperty(ArtifactProperties.LOCAL_PATH, null));
        assertEquals("managed", dep(node, 0, 0).getArtifact().getProperty(ArtifactProperties.LOCAL_PATH, null));
    }

    @Test
    void testDependencyManagement_VerboseMode() throws Exception {
        String depId = "gid:aid2:ext";
        TestDependencyManager depMgmt = new TestDependencyManager();
        depMgmt.version(depId, "managedVersion");
        depMgmt.scope(depId, "managedScope");
        depMgmt.optional(depId, Boolean.TRUE);
        depMgmt.path(depId, "managedPath");
        depMgmt.exclusions(depId, new Exclusion("gid", "aid", "*", "*"));
        session.setDependencyManager(depMgmt);
        session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, Boolean.TRUE);

        CollectRequest request = new CollectRequest().setRoot(newDep("gid:aid:ver"));
        CollectResult result = collector.collectDependencies(session, request);
        DependencyNode node = result.getRoot().getChildren().get(0);
        assertEquals(
                DependencyNode.MANAGED_VERSION
                        | DependencyNode.MANAGED_SCOPE
                        | DependencyNode.MANAGED_OPTIONAL
                        | DependencyNode.MANAGED_PROPERTIES
                        | DependencyNode.MANAGED_EXCLUSIONS,
                node.getManagedBits());
        assertEquals("ver", DependencyManagerUtils.getPremanagedVersion(node));
        assertEquals("compile", DependencyManagerUtils.getPremanagedScope(node));
        assertEquals(Boolean.FALSE, DependencyManagerUtils.getPremanagedOptional(node));
    }

    @Test
    void testDependencyManagement_TransitiveDependencyManager() throws DependencyCollectionException, IOException {
        collector = setupCollector(newReader("managed/"));
        parser = new DependencyGraphParser("artifact-descriptions/managed/");
        session.setDependencyManager(new TransitiveDependencyManager(null));
        final Dependency root = newDep("gid:root:ext:ver", "compile");
        CollectRequest request = new CollectRequest(root, singletonList(repository));
        request.addManagedDependency(newDep("gid:root:ext:must-retain-core-management"));
        CollectResult result = collector.collectDependencies(session, request);

        final DependencyNode expectedTree = parser.parseResource("management-tree.txt");
        assertEqualSubtree(expectedTree, result.getRoot());

        // Same test for root artifact (POM) request.
        final CollectRequest rootArtifactRequest = new CollectRequest();
        rootArtifactRequest.setRepositories(singletonList(repository));
        rootArtifactRequest.setRootArtifact(new DefaultArtifact("gid:root:ext:ver"));
        rootArtifactRequest.addDependency(newDep("gid:direct:ext:ver", "compile"));
        rootArtifactRequest.addManagedDependency(newDep("gid:root:ext:must-retain-core-management"));
        rootArtifactRequest.addManagedDependency(newDep("gid:direct:ext:must-retain-core-management"));
        rootArtifactRequest.addManagedDependency(newDep("gid:transitive-1:ext:managed-by-root"));
        session.setDependencyManager(new TransitiveDependencyManager(null));
        result = collector.collectDependencies(session, rootArtifactRequest);
        assertEqualSubtree(expectedTree, toDependencyResult(result.getRoot(), "compile", null));
    }

    @Test
    void testDependencyManagement_DefaultDependencyManager() throws DependencyCollectionException, IOException {
        collector = setupCollector(newReader("managed/"));
        parser = new DependencyGraphParser("artifact-descriptions/managed/");
        session.setDependencyManager(new DefaultDependencyManager(null));
        final Dependency root = newDep("gid:root:ext:ver", "compile");
        CollectRequest request = new CollectRequest(root, singletonList(repository));
        request.addManagedDependency(newDep("gid:root:ext:must-not-manage-root"));
        request.addManagedDependency(newDep("gid:direct:ext:managed-by-dominant-request"));
        CollectResult result = collector.collectDependencies(session, request);

        final DependencyNode expectedTree = parser.parseResource("default-management-tree.txt");
        assertEqualSubtree(expectedTree, result.getRoot());

        // Same test for root artifact (POM) request.
        final CollectRequest rootArtifactRequest = new CollectRequest();
        rootArtifactRequest.setRepositories(singletonList(repository));
        rootArtifactRequest.setRootArtifact(new DefaultArtifact("gid:root:ext:ver"));
        rootArtifactRequest.addDependency(newDep("gid:direct:ext:ver", "compile"));
        rootArtifactRequest.addManagedDependency(newDep("gid:root:ext:must-not-manage-root"));
        rootArtifactRequest.addManagedDependency(newDep("gid:direct:ext:managed-by-dominant-request"));
        rootArtifactRequest.addManagedDependency(newDep("gid:transitive-1:ext:managed-by-root"));
        session.setDependencyManager(new DefaultDependencyManager(null));
        result = collector.collectDependencies(session, rootArtifactRequest);
        assertEqualSubtree(expectedTree, toDependencyResult(result.getRoot(), "compile", null));
    }

    @Test
    void testTransitiveDepsUseRangesDirtyTree() throws DependencyCollectionException, IOException {
        // Note: DF depends on version order (ultimately the order of versions as returned by VersionRangeResolver
        // that in case of Maven, means order as in maven-metadata.xml
        // BF on the other hand explicitly sorts versions from range in descending order
        //
        // Hence, the "dirty tree" of two will not match.
        DependencyNode root = parser.parseResource(getTransitiveDepsUseRangesDirtyTreeResource());
        Dependency dependency = root.getDependency();
        CollectRequest request = new CollectRequest(dependency, singletonList(repository));

        CollectResult result = collector.collectDependencies(session, request);
        assertEqualSubtree(root, result.getRoot());
    }

    protected abstract String getTransitiveDepsUseRangesDirtyTreeResource();

    @Test
    void testTransitiveDepsUseRangesAndRelocationDirtyTree() throws DependencyCollectionException, IOException {
        // Note: DF depends on version order (ultimately the order of versions as returned by VersionRangeResolver
        // that in case of Maven, means order as in maven-metadata.xml
        // BF on the other hand explicitly sorts versions from range in descending order
        //
        // Hence, the "dirty tree" of two will not match.
        DependencyNode root = parser.parseResource(getTransitiveDepsUseRangesAndRelocationDirtyTreeResource());
        Dependency dependency = root.getDependency();
        CollectRequest request = new CollectRequest(dependency, singletonList(repository));

        CollectResult result = collector.collectDependencies(session, request);
        assertEqualSubtree(root, result.getRoot());
    }

    protected abstract String getTransitiveDepsUseRangesAndRelocationDirtyTreeResource();

    private DependencyNode toDependencyResult(
            final DependencyNode root, final String rootScope, final Boolean optional) {
        // Make the root artifact resolution result a dependency resolution result for the subtree check.
        assertNull(root.getDependency(), "Expected root artifact resolution result.");
        final DefaultDependencyNode defaultNode =
                new DefaultDependencyNode(new Dependency(root.getArtifact(), rootScope));

        defaultNode.setChildren(root.getChildren());

        if (optional != null) {
            defaultNode.setOptional(optional);
        }

        return defaultNode;
    }

    @Test
    void testVersionFilter() throws Exception {
        session.setVersionFilter(new HighestVersionFilter());
        CollectRequest request = new CollectRequest().setRoot(newDep("gid:aid:1"));
        CollectResult result = collector.collectDependencies(session, request);
        assertEquals(1, result.getRoot().getChildren().size());
    }

    @Test
    void testDescriptorDependenciesEmpty() throws Exception {
        collector = setupCollector(newReader("dependencies-empty/"));

        session.setDependencyGraphTransformer(new ConflictResolver(
                new NearestVersionSelector(),
                new JavaScopeSelector(),
                new SimpleOptionalitySelector(),
                new JavaScopeDeriver()));

        DependencyNode root = parser.parseResource("expectedSubtreeOnDescriptorDependenciesEmptyLeft.txt");
        Dependency dependency = root.getDependency();
        CollectRequest request = new CollectRequest(dependency, singletonList(repository));
        CollectResult result = collector.collectDependencies(session, request);
        assertEqualSubtree(root, result.getRoot());

        root = parser.parseResource("expectedSubtreeOnDescriptorDependenciesEmptyRight.txt");
        dependency = root.getDependency();
        request = new CollectRequest(dependency, singletonList(repository));
        result = collector.collectDependencies(session, request);
        assertEqualSubtree(root, result.getRoot());
    }

    static class TestDependencyManager implements DependencyManager {

        private final Map<String, String> versions = new HashMap<>();

        private final Map<String, String> scopes = new HashMap<>();

        private final Map<String, Boolean> optionals = new HashMap<>();

        private final Map<String, String> paths = new HashMap<>();

        private final Map<String, Collection<Exclusion>> exclusions = new HashMap<>();

        public void add(Dependency d, String version, String scope, String localPath) {
            String id = toKey(d);
            version(id, version);
            scope(id, scope);
            path(id, localPath);
        }

        public void version(String id, String version) {
            versions.put(id, version);
        }

        public void scope(String id, String scope) {
            scopes.put(id, scope);
        }

        public void optional(String id, Boolean optional) {
            optionals.put(id, optional);
        }

        public void path(String id, String path) {
            paths.put(id, path);
        }

        public void exclusions(String id, Exclusion... exclusions) {
            this.exclusions.put(id, exclusions != null ? Arrays.asList(exclusions) : null);
        }

        @Override
        public DependencyManagement manageDependency(Dependency dependency) {
            requireNonNull(dependency, "dependency cannot be null");
            String id = toKey(dependency);
            DependencyManagement mgmt = new DependencyManagement();
            mgmt.setVersion(versions.get(id));
            mgmt.setScope(scopes.get(id));
            mgmt.setOptional(optionals.get(id));
            String path = paths.get(id);
            if (path != null) {
                mgmt.setProperties(Collections.singletonMap(ArtifactProperties.LOCAL_PATH, path));
            }
            mgmt.setExclusions(exclusions.get(id));
            return mgmt;
        }

        private String toKey(Dependency dependency) {
            return ArtifactIdUtils.toVersionlessId(dependency.getArtifact());
        }

        @Override
        public DependencyManager deriveChildManager(DependencyCollectionContext context) {
            requireNonNull(context, "context cannot be null");
            return this;
        }
    }
}
