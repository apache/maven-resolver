/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;

/**
 * Collects observed repository events for later inspection.
 */
class RecordingRepositoryListener
    implements RepositoryListener
{

    private List<RepositoryEvent> events = Collections.synchronizedList( new ArrayList<RepositoryEvent>() );

    public List<RepositoryEvent> getEvents()
    {
        return events;
    }

    public void clear()
    {
        events.clear();
    }

    public void artifactDescriptorInvalid( RepositoryEvent event )
    {
        events.add( event );
    }

    public void artifactDescriptorMissing( RepositoryEvent event )
    {
        events.add( event );
    }

    public void metadataInvalid( RepositoryEvent event )
    {
        events.add( event );
    }

    public void artifactResolving( RepositoryEvent event )
    {
        events.add( event );
    }

    public void artifactResolved( RepositoryEvent event )
    {
        events.add( event );
    }

    public void artifactDownloading( RepositoryEvent event )
    {
        events.add( event );
    }

    public void artifactDownloaded( RepositoryEvent event )
    {
        events.add( event );
    }

    public void metadataDownloaded( RepositoryEvent event )
    {
        events.add( event );
    }

    public void metadataDownloading( RepositoryEvent event )
    {
        events.add( event );
    }

    public void metadataResolving( RepositoryEvent event )
    {
        events.add( event );
    }

    public void metadataResolved( RepositoryEvent event )
    {
        events.add( event );
    }

    public void artifactInstalling( RepositoryEvent event )
    {
        events.add( event );
    }

    public void artifactInstalled( RepositoryEvent event )
    {
        events.add( event );
    }

    public void metadataInstalling( RepositoryEvent event )
    {
        events.add( event );
    }

    public void metadataInstalled( RepositoryEvent event )
    {
        events.add( event );
    }

    public void artifactDeploying( RepositoryEvent event )
    {
        events.add( event );
    }

    public void artifactDeployed( RepositoryEvent event )
    {
        events.add( event );
    }

    public void metadataDeploying( RepositoryEvent event )
    {
        events.add( event );
    }

    public void metadataDeployed( RepositoryEvent event )
    {
        events.add( event );
    }

}
