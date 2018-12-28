package org.eclipse.aether.internal.test.util;

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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.VersionScheme;

/**
 * A builder to create dependency nodes for unit testing.
 */
public class NodeBuilder
{

    private String groupId = "test";

    private String artifactId = "";

    private String version = "0.1";

    private String range;

    private String ext = "jar";

    private String classifier = "";

    private String scope = "compile";

    private boolean optional = false;

    private String context;

    private List<Artifact> relocations = new ArrayList<>();

    private VersionScheme versionScheme = new TestVersionScheme();

    private Map<String, String> properties = new HashMap<>( 0 );

    public NodeBuilder artifactId( String artifactId )
    {
        this.artifactId = artifactId;
        return this;
    }

    public NodeBuilder groupId( String groupId )
    {
        this.groupId = groupId;
        return this;

    }

    public NodeBuilder ext( String ext )
    {
        this.ext = ext;
        return this;
    }

    public NodeBuilder version( String version )
    {
        this.version = version;
        this.range = null;
        return this;
    }

    public NodeBuilder range( String range )
    {
        this.range = range;
        return this;
    }

    public NodeBuilder scope( String scope )
    {
        this.scope = scope;
        return this;
    }

    public NodeBuilder optional( boolean optional )
    {
        this.optional = optional;
        return this;
    }

    public NodeBuilder context( String context )
    {
        this.context = context;
        return this;
    }

    public NodeBuilder reloc( String artifactId )
    {
        Artifact relocation = new DefaultArtifact( groupId, artifactId, classifier, ext, version );
        relocations.add( relocation );
        return this;
    }

    public NodeBuilder reloc( String groupId, String artifactId, String version )
    {
        Artifact relocation = new DefaultArtifact( groupId, artifactId, classifier, ext, version );
        relocations.add( relocation );
        return this;
    }

    public NodeBuilder properties( Map<String, String> properties )
    {
        this.properties = properties != null ? properties : Collections.<String, String>emptyMap();
        return this;
    }

    public DependencyNode build()
    {
        Dependency dependency = null;
        if ( artifactId != null && artifactId.length() > 0 )
        {
            Artifact artifact =
                new DefaultArtifact( groupId, artifactId, classifier, ext, version, properties, (File) null );
            dependency = new Dependency( artifact, scope, optional );
        }
        DefaultDependencyNode node = new DefaultDependencyNode( dependency );
        if ( artifactId != null && artifactId.length() > 0 )
        {
            try
            {
                node.setVersion( versionScheme.parseVersion( version ) );
                node.setVersionConstraint( versionScheme.parseVersionConstraint( range != null ? range : version ) );
            }
            catch ( InvalidVersionSpecificationException e )
            {
                throw new IllegalArgumentException( "bad version: " + e.getMessage(), e );
            }
        }
        node.setRequestContext( context );
        node.setRelocations( relocations );
        return node;
    }

}
