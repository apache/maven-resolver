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
package org.eclipse.aether.internal.impl;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.internal.impl.DefaultRepositoryEventDispatcher;
import org.eclipse.aether.internal.test.impl.TestRepositorySystemSession;
import org.junit.Test;

/**
 */
public class DefaultRepositoryEventDispatcherTest
{

    @Test
    public void testDispatchHandlesAllEventTypes()
        throws Exception
    {
        DefaultRepositoryEventDispatcher dispatcher = new DefaultRepositoryEventDispatcher();

        ListenerHandler handler = new ListenerHandler();

        RepositoryListener listener =
            (RepositoryListener) Proxy.newProxyInstance( getClass().getClassLoader(),
                                                         new Class<?>[] { RepositoryListener.class }, handler );

        TestRepositorySystemSession session = new TestRepositorySystemSession();
        session.setRepositoryListener( listener );

        for ( RepositoryEvent.EventType type : RepositoryEvent.EventType.values() )
        {
            RepositoryEvent event = new RepositoryEvent.Builder( session, type ).build();

            handler.methodName = null;

            dispatcher.dispatch( event );

            assertNotNull( "not handled: " + type, handler.methodName );

            assertEquals( "badly handled: " + type, type.name().replace( "_", "" ).toLowerCase( Locale.ENGLISH ),
                          handler.methodName.toLowerCase( Locale.ENGLISH ) );
        }
    }

    static class ListenerHandler
        implements InvocationHandler
    {

        public String methodName;

        public Object invoke( Object proxy, Method method, Object[] args )
            throws Throwable
        {
            if ( args.length == 1 && args[0] instanceof RepositoryEvent )
            {
                methodName = method.getName();
            }

            return null;
        }

    }

}
