package org.eclipse.aether.internal.impl.synccontext.named;

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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * Artifact GAV {@link NameMapper}, uses artifact and metadata coordinates to name their corresponding locks. Is not
 * considering local repository, only the artifact coordinates.
 */
public class GAVNameMapper extends NameMapperSupport
{
    @Override
    protected String getArtifactName( Artifact artifact )
    {
        return "artifact:" + artifact.getGroupId()
                + ":" + artifact.getArtifactId()
                + ":" + artifact.getBaseVersion();
    }

    @Override
    protected String getMetadataName( Metadata metadata )
    {
        String name = "metadata:";
        if ( !metadata.getGroupId().isEmpty() )
        {
            name += metadata.getGroupId();
            if ( !metadata.getArtifactId().isEmpty() )
            {
                name += ":" + metadata.getArtifactId();
                if ( !metadata.getVersion().isEmpty() )
                {
                    name += ":" + metadata.getVersion();
                }
            }
        }
        return name;
    }
}
