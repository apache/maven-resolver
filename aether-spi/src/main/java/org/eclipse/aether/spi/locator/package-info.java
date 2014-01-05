/*******************************************************************************
 * Copyright (c) 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
/**
 * A lightweight service locator infrastructure to help components acquire dependent components. The implementation of
 * the repository system is decomposed into many sub components that interact with each other via interfaces, allowing
 * an application to customize the system by swapping in different implementation classes for these interfaces. The
 * service locator defined by this package is one means for components to get hold of the proper implementation for its
 * dependencies. While not the most popular approach to component wiring, this service locator enables applications
 * that do not wish to pull in more sophisticated solutions like dependency injection containers to have a small
 * footprint. Therefore, all components should implement {@link org.eclipse.aether.spi.locator.Service} to support this
 * goal. 
 */
package org.eclipse.aether.spi.locator;

