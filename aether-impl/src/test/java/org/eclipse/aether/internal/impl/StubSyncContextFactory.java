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
package org.eclipse.aether.internal.impl;

import java.util.Collection;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.SyncContextFactory;
import org.eclipse.aether.metadata.Metadata;

/**
 * 
 */
public class StubSyncContextFactory
    implements SyncContextFactory
{

    public SyncContext newInstance( RepositorySystemSession session, boolean shared )
    {
        return new SyncContext()
        {
            public void release()
            {
            }

            public void acquire( Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas )
            {
            }
        };
    }

}
