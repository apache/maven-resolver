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

import java.util.Collection;
import java.util.TreeSet;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * Support class to implement {@link NameMapper}s. It implements deadlock prevention by "stable ordered result" (sorting
 * the result) and all the null-checks that are needed.
 *
 * @since TBD
 */
public abstract class NameMapperSupport implements NameMapper
{
    @Override
    public Collection<String> nameLocks( final RepositorySystemSession session,
                                         final Collection<? extends Artifact> artifacts,
                                         final Collection<? extends Metadata> metadatas )
    {
        // Deadlock prevention: https://stackoverflow.com/a/16780988/696632
        // We must acquire multiple locks always in the same order!
        TreeSet<String> keys = new TreeSet<>();
        if ( artifacts != null )
        {
            for ( Artifact artifact : artifacts )
            {
                keys.add( getArtifactName( artifact ) );
            }
        }

        if ( metadatas != null )
        {
            for ( Metadata metadata : metadatas )
            {
                keys.add( getMetadataName( metadata ) );
            }
        }
        return keys;
    }

    protected abstract String getArtifactName( final Artifact artifact );

    protected abstract String getMetadataName( final Metadata metadata );
}
