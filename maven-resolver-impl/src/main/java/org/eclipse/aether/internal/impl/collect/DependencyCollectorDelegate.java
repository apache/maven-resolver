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

import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.DependencyCollector;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Helper class for delegate implementations, they MUST subclass this class.
 *
 * @since 1.8.0
 */
public abstract class DependencyCollectorDelegate implements DependencyCollector
{
    protected static final String CONFIG_PROP_MAX_EXCEPTIONS = "aether.dependencyCollector.maxExceptions";

    protected static final int CONFIG_PROP_MAX_EXCEPTIONS_DEFAULT = 50;

    protected static final String CONFIG_PROP_MAX_CYCLES = "aether.dependencyCollector.maxCycles";

    protected static final int CONFIG_PROP_MAX_CYCLES_DEFAULT = 10;

    protected final Logger logger = LoggerFactory.getLogger( getClass() );

    protected RemoteRepositoryManager remoteRepositoryManager;

    protected ArtifactDescriptorReader descriptorReader;

    protected VersionRangeResolver versionRangeResolver;

    public DependencyCollectorDelegate()
    {
        // enables default constructor
    }

    protected DependencyCollectorDelegate( RemoteRepositoryManager remoteRepositoryManager,
                           ArtifactDescriptorReader artifactDescriptorReader,
                           VersionRangeResolver versionRangeResolver )
    {
        setRemoteRepositoryManager( remoteRepositoryManager );
        setArtifactDescriptorReader( artifactDescriptorReader );
        setVersionRangeResolver( versionRangeResolver );
    }

    public void initService( ServiceLocator locator )
    {
        setRemoteRepositoryManager( locator.getService( RemoteRepositoryManager.class ) );
        setArtifactDescriptorReader( locator.getService( ArtifactDescriptorReader.class ) );
        setVersionRangeResolver( locator.getService( VersionRangeResolver.class ) );
    }

    public DependencyCollector setRemoteRepositoryManager( RemoteRepositoryManager remoteRepositoryManager )
    {
        this.remoteRepositoryManager =
                requireNonNull( remoteRepositoryManager, "remote repository manager cannot be null" );
        return this;
    }

    public DependencyCollector setArtifactDescriptorReader( ArtifactDescriptorReader artifactDescriptorReader )
    {
        descriptorReader = requireNonNull( artifactDescriptorReader, "artifact descriptor reader cannot be null" );
        return this;
    }

    public DependencyCollector setVersionRangeResolver( VersionRangeResolver versionRangeResolver )
    {
        this.versionRangeResolver =
                requireNonNull( versionRangeResolver, "version range resolver cannot be null" );
        return this;
    }

    protected void dependencyCollected( RepositorySystemSession session,
                                        List<DependencyNode> path,
                                        Dependency dependency,
                                        ArtifactDescriptorRequest artifactDescriptorRequest,
                                        ArtifactDescriptorResult artifactDescriptorResult )
    {
        logger.info(String.format("%s (context: %s) @ %s", dependency, artifactDescriptorRequest.getRequestContext(),
                artifactDescriptorResult != null && artifactDescriptorResult.getRepository() == null ? "unknown" : artifactDescriptorResult.getRepository().getId()));
        int distance = 0;
        ListIterator<DependencyNode> reversePathIterator = path.listIterator( path.size() );
        while ( reversePathIterator.hasPrevious() )
        {
            DependencyNode dn = reversePathIterator.previous();
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < distance; i++) {
                indent.append("  ");
            }
            distance++;
            indent.append( " -> " );
            logger.info(String.format("%s%s (context: %s) @ %s", indent, dn, dn.getRequestContext(),
                    artifactDescriptorResult.getRepository() == null ? "unknown" : artifactDescriptorResult.getRepository().getId()));
        }
    }
}
