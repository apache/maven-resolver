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

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.DefaultArtifact;
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
     * A validator that rejects any artifact whose version contains "${".
     * This simulates Maven's MavenValidator rejecting uninterpolated expressions.
     */
    private static final ValidatorFactory EXPRESSION_REJECTING_VALIDATOR_FACTORY = session -> new Validator() {
        @Override
        public void validateArtifact(org.eclipse.aether.artifact.Artifact artifact) {
            if (artifact.getVersion().contains("${")) {
                throw new IllegalArgumentException("Uninterpolated expression in version: " + artifact.getVersion());
            }
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
}
