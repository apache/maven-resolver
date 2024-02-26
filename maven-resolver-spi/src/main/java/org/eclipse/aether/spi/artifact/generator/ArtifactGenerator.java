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
package org.eclipse.aether.spi.artifact.generator;

import java.util.Collection;

import org.eclipse.aether.artifact.Artifact;

/**
 * An artifact generator that participates in the installation/deployment of artifacts.
 *
 * @since 2.0.0
 */
public interface ArtifactGenerator extends AutoCloseable {
    /**
     * Returns the generator ID, never {@code null}.
     */
    String generatorId();

    /**
     * Generates artifacts.
     *
     * @param generatedArtifacts The generated artifacts so far.
     * @return The additional artifacts to install/deploy, never {@code null}.
     */
    Collection<? extends Artifact> generate(Collection<? extends Artifact> generatedArtifacts);

    /**
     * Invoked when generator use is done.
     */
    @Override
    void close();
}
