package org.eclipse.aether.spi.resolution;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Artifact resolver post-resolution processor component, is able to hook into resolver and post-process the resolved
 * artifact results, if needed even produce resolution failure. It will always be invoked (even when failure is about
 * to happen), so detecting these cases are left to post processor implementations.
 *
 * @since 1.9.0
 */
public interface ArtifactResolverPostProcessor
{
    /**
     * Receives resolver results just before it would return it to caller. Is able to generate "resolution failure"
     * by augmenting passed in {@link ArtifactResult}s (artifacts should be "unresolved" and exceptions added).
     * <p>
     * Implementations must be aware that the passed in list of {@link ArtifactResult}s may have failed resolutions,
     * best to check that using {@link ArtifactResult#isResolved()} method.
     * <p>
     * The implementations must be aware that this call may be "hot", so it directly affects the performance of
     * resolver in general.
     */
    void postProcess( RepositorySystemSession session, List<ArtifactResult> artifactResults );
}
