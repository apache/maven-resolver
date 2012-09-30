/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.connector.async;

import org.eclipse.aether.repository.RemoteRepository;
import org.sonatype.tests.http.server.api.ServerProvider;
import org.sonatype.tests.http.server.jetty.behaviour.Redirect;

/* *
 */
public class RedirectGetTest
    extends GetTest
{

    @Override
    protected RemoteRepository repository()
    {
        return new RemoteRepository.Builder( super.repository() ).setUrl( url( "redirect" ) ).build();
    }

    @Override
    public void configureProvider( ServerProvider provider )
    {
        super.configureProvider( provider );
        provider().addBehaviour( "/redirect/*", new Redirect( "^", "/repo" ) );
    }

}
