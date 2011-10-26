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
package org.eclipse.aether.spi.locator;

/**
 * A stateless component of the repository system. The primary purpose of this interface is to provide a convenient
 * means to programmatically wire the several components of the repository system together when it is used outside of an
 * IoC container.
 */
public interface Service
{

    /**
     * Provides the opportunity to initialize this service and to acquire other services for its operation from the
     * locator. A service must not save the reference to the provided service locator.
     * 
     * @param locator The service locator, must not be {@code null}.
     */
    void initService( ServiceLocator locator );

}
