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
 * @deprecated Without any direct replacement for now. This API is OOM-prone, and also lacks a lot of context about
 * transforming.
 */
@Deprecated
public interface FileTransformerManager
{
    /**
     * <p>
     * All transformers for this specific artifact. Be aware that if you want to create additional files, but also want
     * to the original to be deployed, you must add an explicit transformer for that file too (one that doesn't
     * transform the artifact and data).
     * </p>
     * 
     * <p><strong>IMPORTANT</strong> When using a fileTransformer, the content of the file is stored in memory to ensure
     * that file content and checksums stay in sync!
     * </p>
     * 
     * @param artifact the artifact
     * @return a collection of FileTransformers to apply on the artifact, never {@code null}
     */
    Collection<FileTransformer> getTransformersForArtifact( Artifact artifact );
}
