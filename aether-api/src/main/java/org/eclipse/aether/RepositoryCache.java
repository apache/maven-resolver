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

/**
 * Caches auxiliary data used during repository access like already processed metadata. The data in the cache is meant
 * for exclusive consumption by the repository system and is opaque to the cache implementation. <strong>Note:</strong>
 * Actual cache implementations must be thread-safe.
 */
public interface RepositoryCache
{

    /**
     * Puts the specified data into the cache. It is entirely up to the cache implementation how long this data will be
     * kept before being purged, i.e. callers must not make any assumptions about the lifetime of cached data.
     * <strong>Warning:</strong> The cache will directly save the provided reference. If the cached data is mutable,
     * i.e. could be modified after being put into the cache, the caller is responsible for creating a copy of the
     * original data and store the copy in the cache.
     * 
     * @param session The repository session during which the cache is accessed, must not be {@code null}.
     * @param key The key to use for lookup of the data, must not be {@code null}.
     * @param data The data to store in the cache, may be {@code null}.
     */
    void put( RepositorySystemSession session, Object key, Object data );

    /**
     * Gets the specified data from the cache. <strong>Warning:</strong> The cache will directly return the saved
     * reference. If the cached data is to be modified after its retrieval, the caller is responsible to create a copy
     * of the returned data and use this instead of the cache record.
     * 
     * @param session The repository session during which the cache is accessed, must not be {@code null}.
     * @param key The key to use for lookup of the data, must not be {@code null}.
     * @return The requested data or {@code null} if none was present in the cache.
     */
    Object get( RepositorySystemSession session, Object key );

}
