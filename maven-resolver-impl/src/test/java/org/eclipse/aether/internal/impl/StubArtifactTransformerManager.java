package org.eclipse.aether.internal.impl;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.transform.ArtifactTransformer;
import org.eclipse.aether.transform.ArtifactTransformerManager;
import org.eclipse.aether.transform.FileTransformer;
import org.eclipse.aether.transform.FileTransformerManager;
import org.eclipse.aether.transform.Identity;

public class StubArtifactTransformerManager implements ArtifactTransformerManager
{
    private final Map<String, Collection<ArtifactTransformer>> artifactTransformers = new HashMap<>();
    
    @Override
    public Collection<ArtifactTransformer> getTransformersForArtifact( Artifact artifact )
    {
        Collection<ArtifactTransformer> transformers = artifactTransformers.get( artifact.getExtension() );
        if ( transformers == null )
        {
            return Collections.singletonList( Identity.TRANSFORMER );
        }
        return transformers;
    }

    public void inhibitTransformer( String extension )
    {
        if ( !artifactTransformers.containsKey( extension ) )
        {
            artifactTransformers.put( extension, new ArrayList<>() );
        }
        artifactTransformers.get( extension ).clear();
    }
    
    public void addFileTransformer( String extension, ArtifactTransformer artifactTransformer )
    {
        if ( !artifactTransformers.containsKey( extension ) )
        {
            artifactTransformers.put( extension, new ArrayList<>() );
        }
        artifactTransformers.get( extension ).add( artifactTransformer );
    }

}
