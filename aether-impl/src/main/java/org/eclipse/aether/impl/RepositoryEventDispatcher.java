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

import org.eclipse.aether.RepositoryEvent;

/**
 * Dispatches repository events to registered listeners.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface RepositoryEventDispatcher
{

    /**
     * Dispatches the specified repository event to all registered listeners.
     * 
     * @param event The event to dispatch, must not be {@code null}.
     */
    void dispatch( RepositoryEvent event );

}
