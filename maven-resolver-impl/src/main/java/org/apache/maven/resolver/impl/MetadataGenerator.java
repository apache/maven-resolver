package org.apache.maven.resolver.impl;

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

import java.util.Collection;

import org.apache.maven.resolver.artifact.Artifact;
import org.apache.maven.resolver.metadata.Metadata;

/**
 * A metadata generator that participates in the installation/deployment of artifacts.
 * 
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface MetadataGenerator
{

    /**
     * Prepares the generator to transform artifacts.
     * 
     * @param artifacts The artifacts to install/deploy, must not be {@code null}.
     * @return The metadata to process (e.g. merge with existing metadata) before artifact transformations, never
     *         {@code null}.
     */
    Collection<? extends Metadata> prepare( Collection<? extends Artifact> artifacts );

    /**
     * Enables the metadata generator to transform the specified artifact.
     * 
     * @param artifact The artifact to transform, must not be {@code null}.
     * @return The transformed artifact (or just the input artifact), never {@code null}.
     */
    Artifact transformArtifact( Artifact artifact );

    /**
     * Allows for metadata generation based on the transformed artifacts.
     * 
     * @param artifacts The (transformed) artifacts to install/deploy, must not be {@code null}.
     * @return The additional metadata to process after artifact transformations, never {@code null}.
     */
    Collection<? extends Metadata> finish( Collection<? extends Artifact> artifacts );

}
