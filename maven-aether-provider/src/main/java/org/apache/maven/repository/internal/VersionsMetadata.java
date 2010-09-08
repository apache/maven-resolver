package org.apache.maven.repository.internal;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.sonatype.aether.artifact.Artifact;

/**
 * @author Benjamin Bentmann
 */
final class VersionsMetadata
    extends MavenMetadata
{

    private final Artifact artifact;

    public VersionsMetadata( Artifact artifact )
    {
        super( createMetadata( artifact ), null );
        this.artifact = artifact;
    }

    public VersionsMetadata( Artifact artifact, File file )
    {
        super( createMetadata( artifact ), file );
        this.artifact = artifact;
    }

    private static Metadata createMetadata( Artifact artifact )
    {
        Versioning versioning = new Versioning();
        versioning.addVersion( artifact.getBaseVersion() );
        if ( !artifact.isSnapshot() )
        {
            versioning.setRelease( artifact.getVersion() );
        }

        Metadata metadata = new Metadata();
        metadata.setVersioning( versioning );
        metadata.setGroupId( artifact.getGroupId() );
        metadata.setArtifactId( artifact.getArtifactId() );

        return metadata;
    }

    public Object getKey()
    {
        return getGroupId() + ':' + getArtifactId();
    }

    public static Object getKey( Artifact artifact )
    {
        return artifact.getGroupId() + ':' + artifact.getArtifactId();
    }

    public MavenMetadata setFile( File file )
    {
        return new VersionsMetadata( artifact, file );
    }

    public String getGroupId()
    {
        return artifact.getGroupId();
    }

    public String getArtifactId()
    {
        return artifact.getArtifactId();
    }

    public String getVersion()
    {
        return "";
    }

    public Nature getNature()
    {
        return artifact.isSnapshot() ? Nature.RELEASE_OR_SNAPSHOT : Nature.RELEASE;
    }

}
