package org.eclipse.aether.util.graph.versions;

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

import static org.junit.Assert.*;

import java.util.Iterator;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;
import org.junit.After;
import org.junit.Before;

public abstract class AbstractVersionFilterTest
{

    protected DefaultRepositorySystemSession session;

    @Before
    public void setUp()
    {
        session = TestUtils.newSession();
    }

    @After
    public void tearDown()
    {
        session = null;
    }

    protected VersionFilter.VersionFilterContext newContext( String gav, String... versions )
    {
        VersionRangeRequest request = new VersionRangeRequest();
        request.setArtifact( new DefaultArtifact( gav ) );
        VersionRangeResult result = new VersionRangeResult( request );
        VersionScheme scheme = new GenericVersionScheme();
        try
        {
            result.setVersionConstraint( scheme.parseVersionConstraint( request.getArtifact().getVersion() ) );
            for ( String version : versions )
            {
                result.addVersion( scheme.parseVersion( version ) );
            }
        }
        catch ( InvalidVersionSpecificationException e )
        {
            throw new IllegalArgumentException( e );
        }
        return TestUtils.newVersionFilterContext( session, result );
    }

    protected VersionFilter derive( VersionFilter filter, String gav )
    {
        return filter.deriveChildFilter( TestUtils.newCollectionContext( session,
                                                                         new Dependency( new DefaultArtifact( gav ), "" ),
                                                                         null ) );
    }

    protected void assertVersions( VersionFilter.VersionFilterContext context, String... versions )
    {
        assertEquals( versions.length, context.getCount() );
        Iterator<Version> it = context.iterator();
        for ( String version : versions )
        {
            assertTrue( it.hasNext() );
            assertEquals( version, it.next().toString() );
        }
    }

}
