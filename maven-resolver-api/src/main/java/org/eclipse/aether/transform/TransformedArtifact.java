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

import java.io.Closeable;
import java.io.IOException;

import org.eclipse.aether.artifact.Artifact;

/**
 * The transformed artifact.
 * 
 * @since 1.9.3
 */
public interface TransformedArtifact extends Artifact, Closeable
{
    /**
     * The transformed artifact.
     *
     * @return the transformed artifact, never {@code null}.
     */
    Artifact getTransformedArtifact();

    /**
     * Closes the handle instance, allowing it to perform any necessary cleanup.
     */
    void close() throws IOException;
}
