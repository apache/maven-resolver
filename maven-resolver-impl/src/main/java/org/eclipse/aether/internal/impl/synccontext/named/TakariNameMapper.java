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
import java.util.Collection;
import java.util.TreeSet;

/**
 * A {@link NameMapper} that creates same name mapping as Takari Local Repository does, without baseDir (local repo).
 * Part of code blatantly copies parts of the Takari {@code LockingSyncContext}.
 *
 * @see <a href="https://github.com/takari/takari-local-repository/blob/master/src/main/java/io/takari/aether/concurrency/LockingSyncContext.java">Takari
 * LockingSyncContext.java</a>
 */
@Singleton
@Named( TakariNameMapper.NAME )
public class TakariNameMapper implements NameMapper
{
    public static final String NAME = "takari";

    private static final char SEPARATOR = '~';

    @Override
    public TreeSet<String> nameLocks( final RepositorySystemSession session,
                                      final Collection<? extends Artifact> artifacts,
                                      final Collection<? extends Metadata> metadatas )
    {
        TreeSet<String> paths = new TreeSet<>();
        if ( artifacts != null )
        {
            for ( Artifact artifact : artifacts )
            {
                paths.add( getPath( artifact ) + ".aetherlock" );
            }
        }
        if ( metadatas != null )
        {
            for ( Metadata metadata : metadatas )
            {
                paths.add( getPath( metadata ) + ".aetherlock" );
            }
        }
        return paths;
    }

    private String getPath( final Artifact artifact )
    {
        // NOTE: Don't use LRM.getPath*() as those paths could be different across processes, e.g. due to staging LRMs.
        return artifact.getGroupId() + SEPARATOR + artifact.getArtifactId() + SEPARATOR + artifact.getBaseVersion();
    }

    private String getPath( final Metadata metadata )
    {
        // NOTE: Don't use LRM.getPath*() as those paths could be different across processes, e.g. due to staging.
        String path = "";
        if ( metadata.getGroupId().length() > 0 )
        {
            path += metadata.getGroupId();
            if ( metadata.getArtifactId().length() > 0 )
            {
                path += SEPARATOR + metadata.getArtifactId();
                if ( metadata.getVersion().length() > 0 )
                {
                    path += SEPARATOR + metadata.getVersion();
                }
            }
        }
        return path;
    }
}