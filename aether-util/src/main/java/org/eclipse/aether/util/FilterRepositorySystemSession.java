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
package org.eclipse.aether.util;

import java.util.Map;

import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SessionData;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.transfer.TransferListener;

/**
 * A repository system session that wraps another session. This class exists merely for the purpose of sub classing
 * where a sub class would intercept one or more methods and present alternative settings to the caller.
 */
public class FilterRepositorySystemSession
    implements RepositorySystemSession
{

    protected RepositorySystemSession session;

    /**
     * Creates a new repository system session that wraps the specified session.
     * 
     * @param session The repository system session to wrap, must not be {@code null}.
     */
    protected FilterRepositorySystemSession( RepositorySystemSession session )
    {
        this.session = session;
    }

    public ArtifactTypeRegistry getArtifactTypeRegistry()
    {
        return session.getArtifactTypeRegistry();
    }

    public AuthenticationSelector getAuthenticationSelector()
    {
        return session.getAuthenticationSelector();
    }

    public RepositoryCache getCache()
    {
        return session.getCache();
    }

    public String getChecksumPolicy()
    {
        return session.getChecksumPolicy();
    }

    public Map<String, Object> getConfigProperties()
    {
        return session.getConfigProperties();
    }

    public DependencyGraphTransformer getDependencyGraphTransformer()
    {
        return session.getDependencyGraphTransformer();
    }

    public DependencyManager getDependencyManager()
    {
        return session.getDependencyManager();
    }

    public DependencySelector getDependencySelector()
    {
        return session.getDependencySelector();
    }

    public DependencyTraverser getDependencyTraverser()
    {
        return session.getDependencyTraverser();
    }

    public LocalRepository getLocalRepository()
    {
        return session.getLocalRepository();
    }

    public LocalRepositoryManager getLocalRepositoryManager()
    {
        return session.getLocalRepositoryManager();
    }

    public MirrorSelector getMirrorSelector()
    {
        return session.getMirrorSelector();
    }

    public ProxySelector getProxySelector()
    {
        return session.getProxySelector();
    }

    public RepositoryListener getRepositoryListener()
    {
        return session.getRepositoryListener();
    }

    public Map<String, String> getSystemProperties()
    {
        return session.getSystemProperties();
    }

    public TransferListener getTransferListener()
    {
        return session.getTransferListener();
    }

    public String getUpdatePolicy()
    {
        return session.getUpdatePolicy();
    }

    public Map<String, String> getUserProperties()
    {
        return session.getUserProperties();
    }

    public WorkspaceReader getWorkspaceReader()
    {
        return session.getWorkspaceReader();
    }

    public boolean isIgnoreInvalidArtifactDescriptor()
    {
        return session.isIgnoreInvalidArtifactDescriptor();
    }

    public boolean isIgnoreMissingArtifactDescriptor()
    {
        return session.isIgnoreMissingArtifactDescriptor();
    }

    public boolean isNotFoundCachingEnabled()
    {
        return session.isNotFoundCachingEnabled();
    }

    public boolean isOffline()
    {
        return session.isOffline();
    }

    public boolean isTransferErrorCachingEnabled()
    {
        return session.isTransferErrorCachingEnabled();
    }

    public SessionData getData()
    {
        return session.getData();
    }

}
