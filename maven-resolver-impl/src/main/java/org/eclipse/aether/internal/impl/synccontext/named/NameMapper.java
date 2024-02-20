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
package org.eclipse.aether.internal.impl.synccontext.named;

import java.util.Collection;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.named.NamedLockKey;

/**
 * Component mapping lock names to passed in artifacts and metadata as required.
 */
public interface NameMapper {
    /**
     * Returns {@code true} if lock names returned by this lock name mapper are file system friendly, can be used
     * as file names and paths.
     * <p>
     * <em>Note:</em> The fact that name mapper is "file system friendly" means ONLY that names it produces CAN be
     * used as file names and paths. Still, it does not mean they will work with ANY file based locking, as for example
     * {@link org.eclipse.aether.named.providers.FileLockNamedLockFactory} expects names as string encoded
     * {@link java.net.URI}s. The only name mapper doing it is {@link BasedirNameMapper}.
     *
     * @since 1.9.0
     */
    boolean isFileSystemFriendly();

    /**
     * Creates (opaque) names for passed in artifacts and metadata. Returned collection has max size of sum of the
     * passed in artifacts and metadata collections, or less. If an empty collection is returned, there will be no
     * locking happening. Never returns {@code null}. The resulting collection MUST BE "stable" (always sorted by
     * same criteria) to avoid deadlocks by acquiring locks in same order, essentially disregarding the order of
     * the input collections.
     * <p>
     * There is no requirement of any kind of "parity" between input element count (sum of two collections, that is)
     * and output collection size, just the returned upper size limit is defined (sum of the passed in two collections
     * size). If returned collection is empty, no locking will happen, if single element, one lock will be used, if two
     * then two named locks will be used etc.
     * <p>
     * Note: name mapper must not use same string for artifacts and metadata, so even the simplest possible
     * implementation like {@link StaticNameMapper} uses two different static strings.
     */
    Collection<NamedLockKey> nameLocks(
            RepositorySystemSession session,
            Collection<? extends Artifact> artifacts,
            Collection<? extends Metadata> metadatas);
}
