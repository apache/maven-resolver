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
 * A file system friendly {@link NameMapper} that creates lock names out of GAV that are file system friendly. The names
 * (when considered as file names) are relative, but are unique to {@link Artifact} and {@link Metadata} GAV
 * coordinates.
 */
public class FileGAVNameMapper extends NameMapperSupport implements FileSystemFriendlyNameMapper
{
    private static final String SEPARATOR = "~";

    private static final String LOCK_SUFFIX = ".lock";

    @Override
    protected String getArtifactName( Artifact artifact )
    {
        return artifact.getGroupId()
                + SEPARATOR + artifact.getArtifactId()
                + SEPARATOR + artifact.getBaseVersion()
                + LOCK_SUFFIX;
    }

    @Override
    protected String getMetadataName( Metadata metadata )
    {
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
        return path + LOCK_SUFFIX;
    }
}
