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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.transform.FileTransformer;
import org.eclipse.aether.transform.FileTransformerManager;

public class StubFileTransformerManager implements FileTransformerManager {
    private Map<String, Collection<FileTransformer>> fileTransformers = new HashMap<>();

    @Override
    public Collection<FileTransformer> getTransformersForArtifact(Artifact artifact) {
        return fileTransformers.get(artifact.getExtension());
    }

    public void addFileTransformer(String extension, FileTransformer fileTransformer) {
        if (!fileTransformers.containsKey(extension)) {
            fileTransformers.put(extension, new HashSet<FileTransformer>());
        }
        fileTransformers.get(extension).add(fileTransformer);
    }
}
