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
package org.eclipse.aether.util.artifact;

import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;

/**
 * An artifact type registry which first consults its own mappings and in case of an unknown type falls back to another
 * type registry.
 */
public final class OverlayArtifactTypeRegistry
    extends SimpleArtifactTypeRegistry
{

    private final ArtifactTypeRegistry delegate;

    /**
     * Creates a new artifact type registry with initially no registered artifact types and the specified fallback
     * registry. Use {@link #add(ArtifactType)} to populate the registry.
     * 
     * @param delegate The artifact type registry to fall back to, may be {@code null}.
     */
    public OverlayArtifactTypeRegistry( ArtifactTypeRegistry delegate )
    {
        this.delegate = delegate;
    }

    /**
     * Adds the specified artifact type to the registry.
     * 
     * @param type The artifact type to add, must not be {@code null}.
     * @return This registry for chaining, never {@code null}.
     */
    public OverlayArtifactTypeRegistry add( ArtifactType type )
    {
        super.add( type );
        return this;
    }

    public ArtifactType get( String typeId )
    {
        ArtifactType type = super.get( typeId );

        if ( type == null && delegate != null )
        {
            type = delegate.get( typeId );
        }

        return type;
    }

}
