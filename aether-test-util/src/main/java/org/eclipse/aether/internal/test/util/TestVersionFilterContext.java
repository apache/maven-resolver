/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.test.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

/**
 */
class TestVersionFilterContext
    implements VersionFilter.VersionFilterContext
{

    private final RepositorySystemSession session;

    private final Dependency dependency;

    private final VersionRangeResult result;

    private final List<Version> versions;

    public TestVersionFilterContext( RepositorySystemSession session, VersionRangeResult result )
    {
        this.session = session;
        this.result = result;
        dependency = new Dependency( result.getRequest().getArtifact(), "" );
        versions = new ArrayList<Version>( result.getVersions() );
    }

    public RepositorySystemSession getSession()
    {
        return session;
    }

    public Dependency getDependency()
    {
        return dependency;
    }

    public int getCount()
    {
        return versions.size();
    }

    public Iterator<Version> iterator()
    {
        return versions.iterator();
    }

    public VersionConstraint getVersionConstraint()
    {
        return result.getVersionConstraint();
    }

    public ArtifactRepository getRepository( Version version )
    {
        return result.getRepository( version );
    }

    public List<RemoteRepository> getRepositories()
    {
        return Collections.unmodifiableList( result.getRequest().getRepositories() );
    }

}
