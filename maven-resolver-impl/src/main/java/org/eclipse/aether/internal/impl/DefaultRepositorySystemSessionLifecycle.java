package org.eclipse.aether.internal.impl;

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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.eclipse.aether.MultiRuntimeException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RepositorySystemSessionLifecycle;

import static java.util.Objects.requireNonNull;

/**
 *
 */
@Singleton
@Named
public class DefaultRepositorySystemSessionLifecycle
        implements RepositorySystemSessionLifecycle
{
    private final ConcurrentHashMap<Integer, RegisteredSession> registeredSessions;

    @Inject
    public DefaultRepositorySystemSessionLifecycle()
    {
        this.registeredSessions = new ConcurrentHashMap<>();
    }

    @Override
    public void sessionStarted( RepositorySystemSession session )
    {
        requireNonNull( session, "session is null" );
        int handle = System.identityHashCode( session );
        registeredSessions.computeIfAbsent( handle, h -> new RegisteredSession( session ) );
    }

    @Override
    public void sessionEnded( RepositorySystemSession session )
    {
        requireNonNull( session, "session is null" );
        int handle = System.identityHashCode( session );
        RegisteredSession registeredSession = registeredSessions.remove( handle );
        if ( registeredSession == null )
        {
            throw new IllegalArgumentException( "session not registered" );
        }
        ArrayList<Exception> exceptions = new ArrayList<>();
        for ( Consumer<RepositorySystemSession> onCloseHandler : registeredSession.handlers )
        {
            try
            {
                onCloseHandler.accept( registeredSession.session );
            }
            catch ( Exception e )
            {
                exceptions.add( e );
            }
        }
        MultiRuntimeException.mayThrow( "session on-close handler failures", exceptions );
    }

    @Override
    public boolean isManaged( RepositorySystemSession session )
    {
        requireNonNull( session, "session is null" );
        int handle = System.identityHashCode( session );
        return registeredSessions.containsKey( handle );
    }

    @Override
    public void addOnSessionEndHandler( RepositorySystemSession session, Consumer<RepositorySystemSession> handler )
    {
        requireNonNull( session, "session is null" );
        requireNonNull( handler, "handler is null" );
        int handle = System.identityHashCode( session );
        RegisteredSession registeredSession = registeredSessions.get( handle );
        if ( registeredSession == null )
        {
            throw new IllegalArgumentException( "session not registered" );
        }
        registeredSession.handlers.add( 0, handler );
    }

    private static class RegisteredSession
    {
        private final RepositorySystemSession session;

        private final CopyOnWriteArrayList<Consumer<RepositorySystemSession>> handlers;

        private RegisteredSession( RepositorySystemSession session )
        {
            this.session = session;
            this.handlers = new CopyOnWriteArrayList<>();
        }
    }
}
