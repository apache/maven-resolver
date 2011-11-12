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
package org.eclipse.aether.repository;

/**
 * Selects a mirror for a given remote repository.
 */
public interface MirrorSelector
{

    /**
     * Selects a mirror for the specified repository.
     * 
     * @param repository The repository to select a mirror for, must not be {@code null}.
     * @return The selected mirror or {@code null} if none.
     * @see RemoteRepository#getMirroredRepositories()
     */
    RemoteRepository getMirror( RemoteRepository repository );

}
