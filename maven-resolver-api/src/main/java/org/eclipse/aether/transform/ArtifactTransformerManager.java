package org.eclipse.aether.transform;

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

import org.eclipse.aether.artifact.Artifact;

/**
 * Manager the FileTransformers 
 * 
 * @author Robert Scholte
 * @since 1.3.0
 */
public interface ArtifactTransformerManager
{
    /**
     * <p>
     * All transformers for this specific artifact. Be aware that if you want to create additional files, but also want
     * to the original to be deployed, you must add an explicit transformer for that file too (one that doesn't
     * transform the artifact and data).
     * </p>
     *
     * @param artifact the artifact
     * @return a collection of FileTransformers to apply on the artifact, never {@code null}
     */
    Collection<ArtifactTransformer> getTransformersForArtifact( Artifact artifact );
}
