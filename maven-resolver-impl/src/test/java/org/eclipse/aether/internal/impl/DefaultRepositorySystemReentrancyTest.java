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
package org.eclipse.aether.internal.impl;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.DependencyCollector;
import org.eclipse.aether.impl.Deployer;
import org.eclipse.aether.impl.Installer;
import org.eclipse.aether.impl.LocalRepositoryProvider;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.StubArtifactDescriptorReader;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.spi.artifact.decorator.ArtifactDecorator;
import org.eclipse.aether.spi.artifact.decorator.ArtifactDecoratorFactory;
import org.eclipse.aether.spi.validator.Validator;
import org.eclipse.aether.spi.validator.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for the re-entrancy detection in {@link DefaultRepositorySystem}.
 *
 * <p>Verifies that when the resolver is called re-entrantly (e.g. Maven 4's
 * model builder → model resolver → RepositorySystem chain), validation and
 * decoration are skipped on inner calls.</p>
 */
public class DefaultRepositorySystemReentrancyTest {

    /**
     * A validator that rejects any artifact or dependency whose version contains "${".
     * This simulates Maven's MavenValidator rejecting uninterpolated expressions.
     */
    private static final ValidatorFactory EXPRESSION_REJECTING_VALIDATOR_FACTORY = session -> new Validator() {
        @Override
        public void validateArtifact(org.eclipse.aether.artifact.Artifact artifact) {
            if (artifact.getVersion().contains("${")) {
                throw new IllegalArgumentException("Uninterpolated expression in version: " + artifact.getVersion());
            }
        }

        @Override
        public void validateDependency(Dependency dependency) {
            validateArtifact(dependency.getArtifact());
        }
    };

    private DefaultRepositorySystem system;
    private DefaultRepositorySystemSession session;
    private AtomicInteger validationCount;

    @BeforeEach
    void init() {
        validationCount = new AtomicInteger(0);
        ValidatorFactory countingValidator = s -> new Validator() {
            @Override
            public void validateArtifact(org.eclipse.aether.artifact.Artifact artifact) {
                validationCount.incrementAndGet();
            }
        };

        system = new DefaultRepositorySystem(
                new StubVersionResolver(),
                new StubVersionRangeResolver(),
                mock(ArtifactResolver.class),
                mock(MetadataResolver.class),
                new StubArtifactDescriptorReader(),
                mock(DependencyCollector.class),
                mock(Installer.class),
                mock(Deployer.class),
                mock(LocalRepositoryProvider.class),
                new StubSyncContextFactory(),
                new DefaultRemoteRepositoryManager(
                        new DefaultUpdatePolicyAnalyzer(),
                        new DefaultChecksumPolicyProvider(),
                        new DefaultRepositoryKeyFunctionFactory()),
                new DefaultRepositorySystemLifecycle(),
                Collections.emptyMap(),
                new DefaultRepositorySystemValidator(Collections.singletonList(countingValidator)));
        session = TestUtils.newSession();
    }

    @Test
    void outermostCallRunsValidation() throws Exception {
        VersionRangeRequest request =
                new VersionRangeRequest(new DefaultArtifact("g:a:1.0"), Collections.emptyList(), null);

        system.resolveVersionRange(session, request);

        assertEquals(1, validationCount.get(), "Outermost call should run validation exactly once");
    }

    @Test
    void reentrantCallSkipsValidation() throws Exception {
        // Simulate a re-entrant call: the trace already contains the RepositorySystem marker.
        // We achieve this by making the outermost call first (which stamps the marker into
        // the trace), then reusing that stamped trace on a second call.
        VersionRangeRequest outerRequest =
                new VersionRangeRequest(new DefaultArtifact("g:a:1.0"), Collections.emptyList(), null);
        system.resolveVersionRange(session, outerRequest);

        assertEquals(1, validationCount.get(), "First call should validate");

        // The outermost call stamped the marker into the trace.
        // Now create an inner request whose trace is a child of the outer's stamped trace
        // — simulating what happens when the model resolver re-enters RepositorySystem
        // during dependency collection.
        RequestTrace outerTrace = outerRequest.getTrace();
        assertNotNull(outerTrace, "Outermost call should have stamped a trace");

        VersionRangeRequest innerRequest =
                new VersionRangeRequest(new DefaultArtifact("g:b:2.0"), Collections.emptyList(), null);
        innerRequest.setTrace(RequestTrace.newChild(outerTrace, "ModelResolver"));

        system.resolveVersionRange(session, innerRequest);

        assertEquals(1, validationCount.get(), "Re-entrant call should NOT run validation again");
    }

    @Test
    void reentrantCallAllowsUninterpolatedExpressions() throws Exception {
        // Build a system with the expression-rejecting validator (simulates MavenValidator)
        DefaultRepositorySystem strictSystem = new DefaultRepositorySystem(
                new StubVersionResolver(),
                new StubVersionRangeResolver(),
                mock(ArtifactResolver.class),
                mock(MetadataResolver.class),
                new StubArtifactDescriptorReader(),
                mock(DependencyCollector.class),
                mock(Installer.class),
                mock(Deployer.class),
                mock(LocalRepositoryProvider.class),
                new StubSyncContextFactory(),
                new DefaultRemoteRepositoryManager(
                        new DefaultUpdatePolicyAnalyzer(),
                        new DefaultChecksumPolicyProvider(),
                        new DefaultRepositoryKeyFunctionFactory()),
                new DefaultRepositorySystemLifecycle(),
                Collections.emptyMap(),
                new DefaultRepositorySystemValidator(
                        Collections.singletonList(EXPRESSION_REJECTING_VALIDATOR_FACTORY)));

        // An outermost call with an uninterpolated expression MUST fail validation
        VersionRangeRequest outerBadRequest =
                new VersionRangeRequest(new DefaultArtifact("g:a:${expr}"), Collections.emptyList(), null);
        assertThrows(IllegalArgumentException.class, () -> strictSystem.resolveVersionRange(session, outerBadRequest));

        // A clean outermost call succeeds and stamps the marker
        VersionRangeRequest outerGoodRequest =
                new VersionRangeRequest(new DefaultArtifact("g:a:1.0"), Collections.emptyList(), null);
        strictSystem.resolveVersionRange(session, outerGoodRequest);
        RequestTrace outerTrace = outerGoodRequest.getTrace();

        // Now a re-entrant call with an uninterpolated expression MUST succeed —
        // the marker in the trace ancestry causes validation to be skipped
        VersionRangeRequest innerBadRequest =
                new VersionRangeRequest(new DefaultArtifact("g:b:${project.version}"), Collections.emptyList(), null);
        innerBadRequest.setTrace(RequestTrace.newChild(outerTrace, "ModelResolver"));

        assertDoesNotThrow(
                () -> strictSystem.resolveVersionRange(session, innerBadRequest),
                "Re-entrant call should skip validation, allowing uninterpolated expressions");
    }

    @Test
    void reentrantReadArtifactDescriptorSkipsDecoration() throws Exception {
        AtomicBoolean decorated = new AtomicBoolean(false);
        ArtifactDecoratorFactory decoratorFactory = new ArtifactDecoratorFactory() {
            @Override
            public ArtifactDecorator newInstance(RepositorySystemSession session) {
                return descriptorResult -> {
                    decorated.set(true);
                    return descriptorResult.getArtifact();
                };
            }

            @Override
            public float getPriority() {
                return 0;
            }
        };

        DefaultRepositorySystem decoratingSystem = new DefaultRepositorySystem(
                new StubVersionResolver(),
                new StubVersionRangeResolver(),
                mock(ArtifactResolver.class),
                mock(MetadataResolver.class),
                new StubArtifactDescriptorReader(),
                mock(DependencyCollector.class),
                mock(Installer.class),
                mock(Deployer.class),
                mock(LocalRepositoryProvider.class),
                new StubSyncContextFactory(),
                new DefaultRemoteRepositoryManager(
                        new DefaultUpdatePolicyAnalyzer(),
                        new DefaultChecksumPolicyProvider(),
                        new DefaultRepositoryKeyFunctionFactory()),
                new DefaultRepositorySystemLifecycle(),
                Collections.singletonMap("test", decoratorFactory),
                new DefaultRepositorySystemValidator(Collections.emptyList()));

        // Outermost call: decorator should run
        ArtifactDescriptorRequest outerRequest =
                new ArtifactDescriptorRequest(new DefaultArtifact("g:a:1.0"), Collections.emptyList(), null);
        decoratingSystem.readArtifactDescriptor(session, outerRequest);
        assertTrue(decorated.get(), "Outermost readArtifactDescriptor should apply decoration");

        // Re-entrant call: decorator should NOT run
        decorated.set(false);
        RequestTrace outerTrace = outerRequest.getTrace();
        ArtifactDescriptorRequest innerRequest =
                new ArtifactDescriptorRequest(new DefaultArtifact("g:b:2.0"), Collections.emptyList(), null);
        innerRequest.setTrace(RequestTrace.newChild(outerTrace, "ModelResolver"));
        decoratingSystem.readArtifactDescriptor(session, innerRequest);
        assertFalse(decorated.get(), "Re-entrant readArtifactDescriptor should skip decoration");
    }

    @Test
    void independentCallsEachValidate() throws Exception {
        // Two independent calls (no shared trace) should each validate
        VersionRangeRequest request1 =
                new VersionRangeRequest(new DefaultArtifact("g:a:1.0"), Collections.emptyList(), null);
        VersionRangeRequest request2 =
                new VersionRangeRequest(new DefaultArtifact("g:b:2.0"), Collections.emptyList(), null);

        system.resolveVersionRange(session, request1);
        system.resolveVersionRange(session, request2);

        assertEquals(2, validationCount.get(), "Two independent calls should each validate");
    }

    @Test
    void outerCallPreservesOriginalTraceData() throws Exception {
        // Plugins (e.g. pgpverify-maven-plugin) walk the RequestTrace chain and cast
        // getData() to Artifact without an instanceof check. The re-entrancy marker
        // must NOT replace the original tip data — it should be inserted below it.
        Object originalData = new Object();
        RequestTrace originalTrace = RequestTrace.newChild(null, originalData);

        VersionRequest request = new VersionRequest();
        request.setArtifact(new DefaultArtifact("g:a:1"));
        request.setRepositories(Collections.emptyList());
        request.setTrace(originalTrace);

        system.resolveVersion(session, request);

        // After the call, the trace tip should still expose the original data
        RequestTrace resultTrace = request.getTrace();
        assertNotNull(resultTrace, "Request should have a trace after the call");
        assertSame(
                originalData,
                resultTrace.getData(),
                "Trace tip data should be the original data, not the re-entrancy marker");

        // The marker should be present deeper in the chain (parent of the tip)
        RequestTrace parent = resultTrace.getParent();
        assertNotNull(parent, "Trace should have a parent containing the re-entrancy marker");
        assertEquals(
                "RepositorySystem", parent.getData().toString(), "Parent trace data should be the re-entrancy marker");
    }

    @Test
    void outerCallWithNullTraceStillStampsMarker() throws Exception {
        // When the original trace is null, the marker should still be stamped
        // (as the tip, since there is no original data to preserve)
        VersionRangeRequest request =
                new VersionRangeRequest(new DefaultArtifact("g:a:1.0"), Collections.emptyList(), null);
        assertNull(request.getTrace(), "Request should start with null trace");

        system.resolveVersionRange(session, request);

        RequestTrace resultTrace = request.getTrace();
        assertNotNull(resultTrace, "Request should have a trace after the call");
        // Re-entrant check should detect the marker
        VersionRangeRequest innerRequest =
                new VersionRangeRequest(new DefaultArtifact("g:b:2.0"), Collections.emptyList(), null);
        innerRequest.setTrace(RequestTrace.newChild(resultTrace, "ModelResolver"));
        int countBefore = validationCount.get();
        system.resolveVersionRange(session, innerRequest);
        assertEquals(countBefore, validationCount.get(), "Re-entrant call should skip validation");
    }

    @Test
    void sessionScopedDetectionSkipsValidationWhenTraceChainIsBroken() throws Exception {
        // Simulates Maven 4's flow where the model builder converts between Maven API traces
        // and resolver traces via RequestTraceHelper, losing the REPOSITORY_SYSTEM_CALL marker.
        //
        // The DependencyCollector, called from within collectDependencies, re-enters
        // RepositorySystem.resolveVersionRange with a FRESH trace (no marker in ancestry)
        // and an uninterpolated expression like ${project.version}. Without session-scoped
        // detection, the validator would reject the expression; with it, the call is
        // detected as re-entrant and validation is skipped.
        AtomicReference<DefaultRepositorySystem> systemRef = new AtomicReference<>();

        DependencyCollector reentrantCollector = (s, request) -> {
            // Inside collectDependencies, simulate a re-entrant call with a FRESH trace
            // (no marker in ancestry — this is the broken path) and an uninterpolated
            // expression. If session-scoped detection fails, the validator rejects this.
            VersionRangeRequest innerRequest = new VersionRangeRequest(
                    new DefaultArtifact("g:inner:${project.version}"), Collections.emptyList(), null);
            // Fresh trace — no REPOSITORY_SYSTEM_CALL marker (simulates Maven's trace conversion)
            innerRequest.setTrace(RequestTrace.newChild(null, "MavenModelResolver"));
            try {
                systemRef.get().resolveVersionRange(s, innerRequest);
            } catch (Exception e) {
                fail("Re-entrant call with broken trace chain should succeed via session-scoped detection: " + e);
            }
            return new CollectResult(request);
        };

        DefaultRepositorySystem strictSystem = new DefaultRepositorySystem(
                new StubVersionResolver(),
                new StubVersionRangeResolver(),
                mock(ArtifactResolver.class),
                mock(MetadataResolver.class),
                new StubArtifactDescriptorReader(),
                reentrantCollector,
                mock(Installer.class),
                mock(Deployer.class),
                mock(LocalRepositoryProvider.class),
                new StubSyncContextFactory(),
                new DefaultRemoteRepositoryManager(
                        new DefaultUpdatePolicyAnalyzer(),
                        new DefaultChecksumPolicyProvider(),
                        new DefaultRepositoryKeyFunctionFactory()),
                new DefaultRepositorySystemLifecycle(),
                Collections.emptyMap(),
                new DefaultRepositorySystemValidator(
                        Collections.singletonList(EXPRESSION_REJECTING_VALIDATOR_FACTORY)));
        systemRef.set(strictSystem);

        // The outermost collectDependencies call should succeed, and the inner
        // resolveVersionRange call (with broken trace and uninterpolated expression)
        // should be detected as re-entrant via the session-scoped depth counter.
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRootArtifact(new DefaultArtifact("g:root:1.0"));

        assertDoesNotThrow(
                () -> strictSystem.collectDependencies(session, collectRequest),
                "Inner call with broken trace chain should succeed via session-scoped detection");
    }

    @Test
    void collectDependenciesAcceptsManagedDepsWithUninterpolatedExpressions() throws Exception {
        // Reproduces the MavenITgh12305 scenario: a BOM imports managed dependencies
        // with uninterpolated expressions like ${osgi.version}. These managed dependencies
        // should be accepted because they are declarative constraints that may never be used.
        DependencyCollector passThroughCollector = (s, request) -> new CollectResult(request);

        DefaultRepositorySystem strictSystem = new DefaultRepositorySystem(
                new StubVersionResolver(),
                new StubVersionRangeResolver(),
                mock(ArtifactResolver.class),
                mock(MetadataResolver.class),
                new StubArtifactDescriptorReader(),
                passThroughCollector,
                mock(Installer.class),
                mock(Deployer.class),
                mock(LocalRepositoryProvider.class),
                new StubSyncContextFactory(),
                new DefaultRemoteRepositoryManager(
                        new DefaultUpdatePolicyAnalyzer(),
                        new DefaultChecksumPolicyProvider(),
                        new DefaultRepositoryKeyFunctionFactory()),
                new DefaultRepositorySystemLifecycle(),
                Collections.emptyMap(),
                new DefaultRepositorySystemValidator(
                        Collections.singletonList(EXPRESSION_REJECTING_VALIDATOR_FACTORY)));

        // Build a CollectRequest with a managed dependency that has an uninterpolated version
        // (like ${osgi.version} from a BOM import)
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRootArtifact(new DefaultArtifact("g:project:1.0"));
        collectRequest.addManagedDependency(new Dependency(
                new DefaultArtifact("org.example:lib-with-undefined-version:${undefined.version}"), "provided"));

        // This should succeed — managed dependencies are not validated because they are
        // declarative constraints, not actual resolution targets
        assertDoesNotThrow(
                () -> strictSystem.collectDependencies(session, collectRequest),
                "Managed dependencies with uninterpolated expressions should be accepted");
    }

    @Test
    void collectDependenciesStillRejectsInvalidDirectDependencies() throws Exception {
        // Verify that direct dependencies (not managed) ARE still validated
        DependencyCollector passThroughCollector = (s, request) -> new CollectResult(request);

        DefaultRepositorySystem strictSystem = new DefaultRepositorySystem(
                new StubVersionResolver(),
                new StubVersionRangeResolver(),
                mock(ArtifactResolver.class),
                mock(MetadataResolver.class),
                new StubArtifactDescriptorReader(),
                passThroughCollector,
                mock(Installer.class),
                mock(Deployer.class),
                mock(LocalRepositoryProvider.class),
                new StubSyncContextFactory(),
                new DefaultRemoteRepositoryManager(
                        new DefaultUpdatePolicyAnalyzer(),
                        new DefaultChecksumPolicyProvider(),
                        new DefaultRepositoryKeyFunctionFactory()),
                new DefaultRepositorySystemLifecycle(),
                Collections.emptyMap(),
                new DefaultRepositorySystemValidator(
                        Collections.singletonList(EXPRESSION_REJECTING_VALIDATOR_FACTORY)));

        // Direct dependency with uninterpolated expression should still be rejected
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRootArtifact(new DefaultArtifact("g:project:1.0"));
        collectRequest.addDependency(
                new Dependency(new DefaultArtifact("org.example:lib:${undefined.version}"), "compile"));

        assertThrows(
                IllegalArgumentException.class,
                () -> strictSystem.collectDependencies(session, collectRequest),
                "Direct dependencies with uninterpolated expressions should still be rejected");
    }

    @Test
    void sessionScopedDepthIsProperlyDecrementedOnExit() throws Exception {
        // Verify that the session-scoped depth counter is properly decremented after
        // a RepositorySystem call completes, so independent calls are still validated
        VersionRangeRequest request1 =
                new VersionRangeRequest(new DefaultArtifact("g:a:1.0"), Collections.emptyList(), null);
        VersionRangeRequest request2 =
                new VersionRangeRequest(new DefaultArtifact("g:b:2.0"), Collections.emptyList(), null);

        system.resolveVersionRange(session, request1);
        int countAfterFirst = validationCount.get();
        assertEquals(1, countAfterFirst, "First call should validate");

        // Second independent call (no shared trace) should also validate
        // because the depth counter should have been decremented on exit
        system.resolveVersionRange(session, request2);
        assertEquals(2, validationCount.get(), "Second independent call should also validate");
    }
}
