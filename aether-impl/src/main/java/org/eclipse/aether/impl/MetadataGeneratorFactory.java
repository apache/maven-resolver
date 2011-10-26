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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.installation.InstallRequest;

/**
 * A factory to create metadata generators. Metadata generators can contribute additional metadata during the
 * installation/deployment of artifacts.
 */
public interface MetadataGeneratorFactory
{

    /**
     * Creates a new metadata generator for the specified install request.
     * 
     * @param session The repository system session from which to configure the generator, must not be {@code null}.
     * @param request The install request the metadata generator is used for, must not be {@code null}.
     * @return The metadata generator for the request or {@code null} if none.
     */
    MetadataGenerator newInstance( RepositorySystemSession session, InstallRequest request );

    /**
     * Creates a new metadata generator for the specified deploy request.
     * 
     * @param session The repository system session from which to configure the generator, must not be {@code null}.
     * @param request The deploy request the metadata generator is used for, must not be {@code null}.
     * @return The metadata generator for the request or {@code null} if none.
     */
    MetadataGenerator newInstance( RepositorySystemSession session, DeployRequest request );

    /**
     * The priority of this factory. Factories with higher priority are invoked before those with lower priority.
     * 
     * @return The priority of this factory.
     */
    float getPriority();

}
