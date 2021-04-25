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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Artifact GAV {@link NameMapper}, uses artifact and metadata coordinates to name their corresponding locks. Is not
 * considering local repository, only the artifact coordinates.
 */
@Singleton
@Named( GAVNameMapper.NAME )
public class GAVNameMapper implements NameMapper
{
    public static final String NAME = "gav";

    @Override
    public String nameLock( final RepositorySystemSession session, final Artifact artifact )
    {
        return "artifact:" + artifact.getGroupId()
               + ":" + artifact.getArtifactId()
               + ":" + artifact.getBaseVersion();
    }

    @Override
    public String nameLock( final RepositorySystemSession session, final Metadata metadata )
    {
        StringBuilder key = new StringBuilder( "metadata:" );
        if ( !metadata.getGroupId().isEmpty() )
        {
            key.append( metadata.getGroupId() );
            if ( !metadata.getArtifactId().isEmpty() )
            {
                key.append( ':' ).append( metadata.getArtifactId() );
                if ( !metadata.getVersion().isEmpty() )
                {
                    key.append( ':' ).append( metadata.getVersion() );
                }
            }
        }
        return key.toString();
    }
}
