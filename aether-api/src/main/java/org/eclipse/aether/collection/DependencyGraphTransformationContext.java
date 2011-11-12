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
package org.eclipse.aether.collection;

import org.eclipse.aether.RepositorySystemSession;

/**
 * A context used during dependency collection to exchange information within a chain of dependency graph transformers.
 * 
 * @see DependencyGraphTransformer
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface DependencyGraphTransformationContext
{

    /**
     * Gets the repository system session during which the graph transformation happens.
     * 
     * @return The repository system session, never {@code null}.
     */
    RepositorySystemSession getSession();

    /**
     * Gets a keyed value from the context.
     * 
     * @param key The key used to query the value, must not be {@code null}.
     * @return The queried value or {@code null} if none.
     */
    Object get( Object key );

    /**
     * Puts a keyed value into the context.
     * 
     * @param key The key used to store the value, must not be {@code null}.
     * @param value The value to store, may be {@code null}.
     * @return The previous value associated with the key or {@code null} if none.
     */
    Object put( Object key, Object value );

}
