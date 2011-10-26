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

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;

/**
 * This collector fulfills the contract of
 * {@link RepositorySystem#collectDependencies(RepositorySystemSession, CollectRequest)}.
 */
public interface DependencyCollector
{

    /**
     * @see RepositorySystem#collectDependencies(RepositorySystemSession, CollectRequest)
     */
    CollectResult collectDependencies( RepositorySystemSession session, CollectRequest request )
        throws DependencyCollectionException;

}
