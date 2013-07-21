/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.transport.http;

import java.util.LinkedList;

import org.apache.http.auth.AuthScheme;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.impl.auth.BasicScheme;

/**
 * Pool of (equivalent) auth schemes for a single host.
 */
final class AuthSchemePool
{

    private final LinkedList<AuthScheme> authSchemes;

    private String schemeName;

    public AuthSchemePool()
    {
        authSchemes = new LinkedList<AuthScheme>();
    }

    public synchronized AuthScheme get()
    {
        AuthScheme authScheme = null;
        if ( !authSchemes.isEmpty() )
        {
            authScheme = authSchemes.removeLast();
        }
        else if ( AuthPolicy.BASIC.equalsIgnoreCase( schemeName ) )
        {
            authScheme = new BasicScheme();
        }
        return authScheme;
    }

    public synchronized void put( AuthScheme authScheme )
    {
        if ( authScheme == null )
        {
            return;
        }
        if ( !authScheme.getSchemeName().equals( schemeName ) )
        {
            schemeName = authScheme.getSchemeName();
            authSchemes.clear();
        }
        authSchemes.add( authScheme );
    }

}
