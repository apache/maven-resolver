/*******************************************************************************
 * Copyright (c) 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;

class StubRepositoryConnectorProvider
    implements RepositoryConnectorProvider
{

    public StubRepositoryConnectorProvider( RepositoryConnector connector )
    {
        setConnector( connector );
    }

    public StubRepositoryConnectorProvider()
    {
    }

    private RepositoryConnector connector;

    public void setConnector( RepositoryConnector connector )
    {
        this.connector = connector;
    }

    public RepositoryConnector newRepositoryConnector( RepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryConnectorException
    {
        return connector;
    }

}
