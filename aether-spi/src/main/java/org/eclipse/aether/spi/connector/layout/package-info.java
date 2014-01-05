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
 * The contract to locate URI-based resources using custom repository layouts. By implementing a
 * {@link org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory} and registering it with the repository
 * system, an application enables access to remote repositories that use new content types/layouts.  
 */
package org.eclipse.aether.spi.connector.layout;

