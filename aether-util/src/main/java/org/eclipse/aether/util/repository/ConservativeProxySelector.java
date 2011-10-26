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
package org.eclipse.aether.util.repository;

import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A proxy selector that delegates to another selector but only if a repository has no proxy yet. If a proxy has already
 * been assigned to a repository, that is selected.
 */
public final class ConservativeProxySelector
    implements ProxySelector
{

    private final ProxySelector selector;

    /**
     * Creates a new selector that delegates to the specified selector.
     * 
     * @param selector The selector to delegate to in case a repository has no proxy yet, must not be {@code null}.
     */
    public ConservativeProxySelector( ProxySelector selector )
    {
        if ( selector == null )
        {
            throw new IllegalArgumentException( "no proxy selector specified" );
        }
        this.selector = selector;
    }

    public Proxy getProxy( RemoteRepository repository )
    {
        Proxy proxy = repository.getProxy();
        if ( proxy != null )
        {
            return proxy;
        }
        return selector.getProxy( repository );
    }

}
