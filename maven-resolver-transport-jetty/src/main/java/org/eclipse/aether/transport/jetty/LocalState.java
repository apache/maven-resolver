package org.eclipse.aether.transport.jetty;

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

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Container for HTTP-related state that can be shared across invocations of the transporter to optimize the
 * communication with server.
 */
final class LocalState
{
    private static final String EXPECT_CONTINUE_KEY = LocalState.class.getName() + ".expectContinue.";

    private static final String WEBDAV_KEY = LocalState.class.getName() + ".webDav.";

    private final AtomicReference<Boolean> expectContinue;

    private final AtomicReference<Boolean> webDav;

    @SuppressWarnings( "unchecked" )
    LocalState( RepositorySystemSession session, RemoteRepository repo )
    {
        this.expectContinue = (AtomicReference<Boolean>) session.getData()
                .computeIfAbsent( EXPECT_CONTINUE_KEY + repo.getId(), () -> new AtomicReference<>( null ) );
        this.webDav = (AtomicReference<Boolean>) session.getData()
                .computeIfAbsent( WEBDAV_KEY + repo.getId(), () -> new AtomicReference<>( null ) );
    }

    public boolean isExpectContinue()
    {
        return !Boolean.FALSE.equals( this.expectContinue.get() );
    }

    public void setExpectContinue( boolean enabled )
    {
        this.expectContinue.set( enabled );
    }

    public Boolean getWebDav()
    {
        return webDav.get();
    }

    public void setWebDav( boolean webDav )
    {
        this.webDav.set( webDav );
    }
}
