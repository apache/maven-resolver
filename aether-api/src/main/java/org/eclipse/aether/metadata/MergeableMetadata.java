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
package org.eclipse.aether.metadata;

import java.io.File;

import org.eclipse.aether.RepositoryException;

/**
 * A piece of metadata that needs to be merged with any current metadata before installation/deployment.
 */
public interface MergeableMetadata
    extends Metadata
{

    /**
     * Merges this metadata into the current metadata (if any). Note that this method will be invoked regardless whether
     * metadata currently exists or not.
     * 
     * @param current The path to the current metadata file, may not exist but must not be {@code null}.
     * @param result The path to the result file where the merged metadata should be stored, must not be {@code null}.
     * @throws RepositoryException If the metadata could not be merged.
     */
    void merge( File current, File result )
        throws RepositoryException;

    /**
     * Indicates whether this metadata has been merged.
     * 
     * @return {@code true} if the metadata has been merged, {@code false} otherwise.
     */
    boolean isMerged();

}
