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
package org.eclipse.aether.impl;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;

/**
 * A factory to create synchronization contexts. A synchronization context is used to coordinate concurrent access to
 * artifacts or metadata.
 */
public interface SyncContextFactory
{

    /**
     * Creates a new synchronization context.
     * 
     * @param session The repository session during which the context will be used, must not be {@code null}.
     * @param shared A flag indicating whether access to the artifacts/metadata associated with the new context can be
     *            shared among concurrent readers or whether access needs to be exclusive to the calling thread.
     * @return The synchronization context, never {@code null}.
     */
    SyncContext newInstance( RepositorySystemSession session, boolean shared );

}
