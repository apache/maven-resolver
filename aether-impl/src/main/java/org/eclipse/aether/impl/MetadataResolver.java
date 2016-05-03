package org.eclipse.aether.impl;

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
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;

/**
 * Resolves metadata, that is gets a local filesystem path to their binary contents.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface MetadataResolver
{

    /**
     * Resolves the paths for a collection of metadata. Metadata will be downloaded to the local repository if
     * necessary, e.g. because it hasn't been cached yet or the cache is deemed outdated.
     * 
     * @param session The repository session, must not be {@code null}.
     * @param requests The resolution requests, must not be {@code null}.
     * @return The resolution results (in request order), never {@code null}.
     * @see Metadata#getFile()
     * @see RepositorySystem#resolveMetadata(RepositorySystemSession, Collection)
     */
    List<MetadataResult> resolveMetadata( RepositorySystemSession session,
                                          Collection<? extends MetadataRequest> requests );

}
