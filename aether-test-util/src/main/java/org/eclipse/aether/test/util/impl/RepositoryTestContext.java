/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.test.util.impl;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.test.impl.RecordingRepositoryListener;
import org.eclipse.aether.test.impl.TestRepositorySystemSession;

public class RepositoryTestContext
{
    TestRepositorySystemSession session;

    Artifact artifact;

    public TestRepositorySystemSession getSession()
    {
        return session;
    }

    public void setSession( TestRepositorySystemSession session )
    {
        this.session = session;
    }

    public Artifact getArtifact()
    {
        return artifact;
    }

    public void setArtifact( Artifact artifact )
    {
        this.artifact = artifact;
    }

    public RecordingRepositoryListener getRecordingRepositoryListener()
    {
        if ( session.getRepositoryListener() instanceof RecordingRepositoryListener )
        {
            return (RecordingRepositoryListener) session.getRepositoryListener();
        }
        else
        {
            return new RecordingRepositoryListener( session.getRepositoryListener() );
        }
    }
}
