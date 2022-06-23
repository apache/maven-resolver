package org.apache.maven.resolver;

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
import java.util.Collection;

import org.apache.maven.resolver.artifact.Artifact;
import org.apache.maven.resolver.metadata.Metadata;

/**
 * A synchronization context used to coordinate concurrent access to artifacts or metadatas. The typical usage of a
 * synchronization context looks like this:
 * 
 * <pre>
 * SyncContext syncContext = repositorySystem.newSyncContext( ... );
 * try {
 *     syncContext.acquire( artifacts, metadatas );
 *     // work with the artifacts and metadatas
 * } finally {
 *     syncContext.close();
 * }
 * </pre>
 * 
 * Within one thread, synchronization contexts may be nested which can naturally happen in a hierarchy of method calls.
 * The nested synchronization contexts may also acquire overlapping sets of artifacts/metadatas as long as the following
 * conditions are met. If the outer-most context holding a particular resource is exclusive, that resource can be
 * reacquired in any nested context. If however the outer-most context is shared, the resource may only be reacquired by
 * nested contexts if these are also shared.
 * <p>
 * A synchronization context is meant to be utilized by only one thread and as such is not thread-safe.
 * <p>
 * Note that the level of actual synchronization is subject to the implementation and might range from OS-wide to none.
 * 
 * @see RepositorySystem#newSyncContext(RepositorySystemSession, boolean)
 */
public interface SyncContext
    extends Closeable
{

    /**
     * Acquires synchronized access to the specified artifacts and metadatas. The invocation will potentially block
     * until all requested resources can be acquired by the calling thread. Acquiring resources that are already
     * acquired by this synchronization context has no effect. Please also see the class-level documentation for
     * information regarding reentrancy. The method may be invoked multiple times on a synchronization context until all
     * desired resources have been acquired.
     * 
     * @param artifacts The artifacts to acquire, may be {@code null} or empty if none.
     * @param metadatas The metadatas to acquire, may be {@code null} or empty if none.
     */
    void acquire( Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas );

    /**
     * Releases all previously acquired artifacts/metadatas. If no resources have been acquired before or if this
     * synchronization context has already been closed, this method does nothing.
     */
    void close();

}
