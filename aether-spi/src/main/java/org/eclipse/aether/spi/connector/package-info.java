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
 * The contract to access artifacts/metadata in remote repositories. By implementing a
 * {@link org.eclipse.aether.spi.connector.RepositoryConnectorFactory} and registering it with the repository system,
 * an application can enable access to arbitrary remote repositories. It should be noted that a repository connector is
 * powerful yet burdensome to implement. In many cases, implementing a 
 * {@link org.eclipse.aether.spi.connector.transport.TransporterFactory} or 
 * {@link org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory} will be sufficient and easier to access a
 * custom remote repository.
 */
package org.eclipse.aether.spi.connector;

