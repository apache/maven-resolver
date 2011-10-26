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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;

/**
 * A simple map-based artifact type registry.
 */
class SimpleArtifactTypeRegistry
    implements ArtifactTypeRegistry
{

    private final Map<String, ArtifactType> types;

    /**
     * Creates a new artifact type registry with initally no registered artifact types. Use {@link #add(ArtifactType)}
     * to populate the registry.
     */
    public SimpleArtifactTypeRegistry()
    {
        types = new HashMap<String, ArtifactType>();
    }

    /**
     * Adds the specified artifact type to the registry.
     * 
     * @param type The artifact type to add, must not be {@code null}.
     * @return This registry for chaining, never {@code null}.
     */
    public SimpleArtifactTypeRegistry add( ArtifactType type )
    {
        types.put( type.getId(), type );
        return this;
    }

    public ArtifactType get( String typeId )
    {
        ArtifactType type = types.get( typeId );

        return type;
    }

    @Override
    public String toString()
    {
        return types.toString();
    }

}
