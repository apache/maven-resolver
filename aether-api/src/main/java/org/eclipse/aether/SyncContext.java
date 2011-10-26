/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether;

import java.util.Collection;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

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
 *     syncContext.release();
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
 */
public interface SyncContext
{

    /**
     * Acquires synchronized access to the specified artifacts and metadatas. The invocation will potentially block
     * until all requested resources can be acquired by the calling thread. Acquiring resources that are already
     * acquired by this synchronization context has no effect. Please also see the class-level documentation for
     * information regarding reentrancy.
     * 
     * @param artifacts The artifacts to acquire, may be {@code null} or empty if none.
     * @param metadatas The metadatas to acquire, may be {@code null} or empty if none.
     */
    void acquire( Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas );

    /**
     * Releases all previously acquired artifacts/metadatas. If no resources have been acquired before, this method does
     * nothing. This synchronization context may be reused to acquire other resources in the future.
     */
    void release();

}
