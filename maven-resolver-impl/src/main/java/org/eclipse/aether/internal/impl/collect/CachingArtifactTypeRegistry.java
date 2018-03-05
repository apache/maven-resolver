package org.eclipse.aether.internal.impl.collect;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;

/**
 * A short-lived artifact type registry that caches results from a presumedly slower type registry.
 */
class CachingArtifactTypeRegistry
    implements ArtifactTypeRegistry
{

    private final ArtifactTypeRegistry delegate;

    private final Map<String, ArtifactType> types;

    public static ArtifactTypeRegistry newInstance( RepositorySystemSession session )
    {
        return newInstance( session.getArtifactTypeRegistry() );
    }

    public static ArtifactTypeRegistry newInstance( ArtifactTypeRegistry delegate )
    {
        return ( delegate != null ) ? new CachingArtifactTypeRegistry( delegate ) : null;
    }

    private CachingArtifactTypeRegistry( ArtifactTypeRegistry delegate )
    {
        this.delegate = delegate;
        types = new HashMap<String, ArtifactType>();
    }

    public ArtifactType get( String typeId )
    {
        ArtifactType type = types.get( typeId );

        if ( type == null )
        {
            type = delegate.get( typeId );
            types.put( typeId, type );
        }

        return type;
    }

}
