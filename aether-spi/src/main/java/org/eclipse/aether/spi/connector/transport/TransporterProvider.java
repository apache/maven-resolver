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
package org.eclipse.aether.spi.connector.transport;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Retrieves a transporter from the installed transporter factories.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface TransporterProvider
{

    /**
     * Tries to create a transporter for the specified remote repository.
     * 
     * @param session The repository system session from which to configure the transporter, must not be {@code null}.
     * @param repository The remote repository to create a transporter for, must not be {@code null}.
     * @return The transporter for the given repository, never {@code null}.
     * @throws NoTransporterException If the factory cannot create a transporter for the specified remote repository.
     */
    Transporter newTransporter( RepositorySystemSession session, RemoteRepository repository )
        throws NoTransporterException;

}
