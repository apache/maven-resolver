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

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.deployment.DeployRequest;
import org.sonatype.aether.impl.MetadataGenerator;
import org.sonatype.aether.installation.InstallRequest;
import org.sonatype.aether.metadata.Metadata;

/**
 * @author Benjamin Bentmann
 */
class VersionsMetadataGenerator
    implements MetadataGenerator
{

    private Map<Object, VersionsMetadata> versions;

    private Map<Object, VersionsMetadata> processedVersions;

    public VersionsMetadataGenerator( RepositorySystemSession session, InstallRequest request )
    {
        this( session, request.getMetadata() );
    }

    public VersionsMetadataGenerator( RepositorySystemSession session, DeployRequest request )
    {
        this( session, request.getMetadata() );
    }

    private VersionsMetadataGenerator( RepositorySystemSession session, Collection<? extends Metadata> metadatas )
    {
        versions = new LinkedHashMap<Object, VersionsMetadata>();
        processedVersions = new LinkedHashMap<Object, VersionsMetadata>();

        /*
         * NOTE: This should be considered a quirk to support interop with Maven's legacy ArtifactDeployer which
         * processes one artifact at a time and hence cannot associate the artifacts from the same project to use the
         * same version index. Allowing the caller to pass in metadata from a previous deployment allows to re-establish
         * the association between the artifacts of the same project.
         */
        for ( Iterator<? extends Metadata> it = metadatas.iterator(); it.hasNext(); )
        {
            Metadata metadata = it.next();
            if ( metadata instanceof VersionsMetadata )
            {
                it.remove();
                VersionsMetadata versionsMetadata = (VersionsMetadata) metadata;
                processedVersions.put( versionsMetadata.getKey(), versionsMetadata );
            }
        }
    }

    public Collection<? extends Metadata> prepare( Collection<? extends Artifact> artifacts )
    {
        return Collections.emptyList();
    }

    public Artifact transformArtifact( Artifact artifact )
    {
        return artifact;
    }

    public Collection<? extends Metadata> finish( Collection<? extends Artifact> artifacts )
    {
        for ( Artifact artifact : artifacts )
        {
            Object key = VersionsMetadata.getKey( artifact );
            if ( processedVersions.get( key ) == null )
            {
                VersionsMetadata versionsMetadata = versions.get( key );
                if ( versionsMetadata == null )
                {
                    versionsMetadata = new VersionsMetadata( artifact );
                    versions.put( key, versionsMetadata );
                }
            }
        }

        return versions.values();
    }

}
