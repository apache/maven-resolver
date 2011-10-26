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

import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;

/**
 */
public interface RemoteRepositoryManager
{

    List<RemoteRepository> aggregateRepositories( RepositorySystemSession session,
                                                  List<RemoteRepository> dominantRepositories,
                                                  List<RemoteRepository> recessiveRepositories, boolean recessiveIsRaw );

    RepositoryPolicy getPolicy( RepositorySystemSession session, RemoteRepository repository, boolean releases,
                                boolean snapshots );

    RepositoryConnector getRepositoryConnector( RepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryConnectorException;

}
