package org.eclipse.aether.transport.http;

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

    AuthSchemePool()
    {
        authSchemes = new LinkedList<>();
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
