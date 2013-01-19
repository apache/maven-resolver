/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.artifact;

import org.eclipse.aether.artifact.ArtifactType;

/**
 * A simple artifact type registry.
 */
public final class DefaultArtifactTypeRegistry
    extends SimpleArtifactTypeRegistry
{

    /**
     * Creates a new artifact type registry with initally no registered artifact types. Use {@link #add(ArtifactType)}
     * to populate the registry.
     */
    public DefaultArtifactTypeRegistry()
    {
    }

    /**
     * Adds the specified artifact type to the registry.
     * 
     * @param type The artifact type to add, must not be {@code null}.
     * @return This registry for chaining, never {@code null}.
     */
    public DefaultArtifactTypeRegistry add( ArtifactType type )
    {
        super.add( type );
        return this;
    }

}
