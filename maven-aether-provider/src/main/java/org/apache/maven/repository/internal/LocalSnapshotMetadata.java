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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.sonatype.aether.artifact.Artifact;

/**
 * @author Benjamin Bentmann
 */
final class LocalSnapshotMetadata
    extends MavenSnapshotMetadata
{

    public LocalSnapshotMetadata( Artifact artifact, boolean legacyFormat )
    {
        super( createLocalMetadata( artifact, legacyFormat ), null, legacyFormat );
    }

    public LocalSnapshotMetadata( Metadata metadata, File file, boolean legacyFormat )
    {
        super( metadata, file, legacyFormat );
    }

    private static Metadata createLocalMetadata( Artifact artifact, boolean legacyFormat )
    {
        Metadata metadata = createRepositoryMetadata( artifact, legacyFormat );

        Snapshot snapshot = new Snapshot();
        snapshot.setLocalCopy( true );
        Versioning versioning = new Versioning();
        versioning.setSnapshot( snapshot );

        metadata.setVersioning( versioning );

        return metadata;
    }

    public MavenMetadata setFile( File file )
    {
        return new LocalSnapshotMetadata( metadata, file, legacyFormat );
    }

    @Override
    protected void merge( Metadata recessive )
    {
        metadata.getVersioning().updateTimestamp();

        if ( !legacyFormat )
        {
            String lastUpdated = metadata.getVersioning().getLastUpdated();

            Map<String, SnapshotVersion> versions = new LinkedHashMap<String, SnapshotVersion>();

            for ( Artifact artifact : artifacts )
            {
                SnapshotVersion sv = new SnapshotVersion();
                sv.setClassifier( artifact.getClassifier() );
                sv.setExtension( artifact.getExtension() );
                sv.setVersion( getVersion() );
                sv.setUpdated( lastUpdated );
                versions.put( getKey( sv.getClassifier(), sv.getExtension() ), sv );
            }

            Versioning versioning = recessive.getVersioning();
            if ( versioning != null )
            {
                for ( SnapshotVersion sv : versioning.getSnapshotVersions() )
                {
                    String key = getKey( sv.getClassifier(), sv.getExtension() );
                    if ( !versions.containsKey( key ) )
                    {
                        versions.put( key, sv );
                    }
                }
            }

            metadata.getVersioning().setSnapshotVersions( new ArrayList<SnapshotVersion>( versions.values() ) );
        }

        artifacts.clear();
    }

}
