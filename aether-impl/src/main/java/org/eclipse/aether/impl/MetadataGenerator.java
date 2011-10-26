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

import java.util.Collection;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * A metadata generator that participates in the installation/deployment of artifacts.
 */
public interface MetadataGenerator
{

    /**
     * Prepares the generator to transform artifacts.
     * 
     * @param artifacts The artifacts to install/deploy, must not be {@code null}.
     * @return The metadata to process (e.g. merge with existing metadata) before artifact transformations, never
     *         {@code null}.
     */
    Collection<? extends Metadata> prepare( Collection<? extends Artifact> artifacts );

    /**
     * Enables the metadata generator to transform the specified artifact.
     * 
     * @param artifact The artifact to transform, must not be {@code null}.
     * @return The transformed artifact (or just the input artifact), never {@code null}.
     */
    Artifact transformArtifact( Artifact artifact );

    /**
     * Allows for metadata generation based on the transformed artifacts.
     * 
     * @param artifacts The (transformed) artifacts to install/deploy, must not be {@code null}.
     * @return The additional metadata to process after artifact transformations, never {@code null}.
     */
    Collection<? extends Metadata> finish( Collection<? extends Artifact> artifacts );

}
