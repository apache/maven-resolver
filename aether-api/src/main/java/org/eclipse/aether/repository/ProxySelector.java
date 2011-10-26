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
package org.eclipse.aether.repository;


/**
 * Selects a proxy for a given remote repository.
 */
public interface ProxySelector
{

    /**
     * Selects a proxy for the specified remote repository.
     * 
     * @param repository The repository for which to select a proxy, must not be {@code null}.
     * @return The selected proxy or {@code null} if none.
     */
    Proxy getProxy( RemoteRepository repository );

}
