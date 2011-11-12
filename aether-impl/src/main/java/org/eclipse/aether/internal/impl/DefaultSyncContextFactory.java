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

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.SyncContextFactory;
import org.eclipse.aether.metadata.Metadata;

/**
 * A factory to create synchronization contexts. This default implementation actually does not provide any real
 * synchronization but merely completes the repository system.
 */
@Component( role = SyncContextFactory.class )
public class DefaultSyncContextFactory
    implements SyncContextFactory
{

    public SyncContext newInstance( RepositorySystemSession session, boolean shared )
    {
        return new DefaultSyncContext();
    }

    static class DefaultSyncContext
        implements SyncContext
    {

        public void acquire( Collection<? extends Artifact> artifact, Collection<? extends Metadata> metadata )
        {
        }

        public void close()
        {
        }

    }

}
