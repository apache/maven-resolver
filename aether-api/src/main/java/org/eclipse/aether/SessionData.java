/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
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
 * A container for data that is specific to a repository system session. Both components within the repository system
 * and clients of the system may use this storage to associate arbitrary data with a session. Unlike a cache, this
 * session data is not subject to purging. For this same reason, session data should also not be abused as a cache (i.e.
 * for storing values that can be re-calculated) to avoid memory exhaustion. <strong>Note:</strong> Actual
 * implementations must be thread-safe.
 * 
 * @see RepositorySystemSession#getData()
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface SessionData
{

    /**
     * Associates the specified session data with the given key.
     * 
     * @param key The key under which to store the session data, must not be {@code null}.
     * @param value The data to associate with the key, may be {@code null} to remove the mapping.
     */
    void set( Object key, Object value );

    /**
     * Associates the specified session data with the given key if the key is currently mapped to the given value. This
     * method provides an atomic compare-and-update of some key's value.
     * 
     * @param key The key under which to store the session data, must not be {@code null}.
     * @param oldValue The expected data currently associated with the key, may be {@code null}.
     * @param newValue The data to associate with the key, may be {@code null} to remove the mapping.
     * @return {@code true} if the key mapping was updated to the specified value, {@code false} if the current key
     *         mapping didn't match the expected value and was not updated.
     */
    boolean set( Object key, Object oldValue, Object newValue );

    /**
     * Gets the session data associated with the specified key.
     * 
     * @param key The key for which to retrieve the session data, must not be {@code null}.
     * @return The session data associated with the key or {@code null} if none.
     */
    Object get( Object key );

}
