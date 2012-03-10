/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.test.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SessionData;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.resolution.ResolutionErrorPolicyRequest;
import org.eclipse.aether.transfer.TransferListener;

public class TestRepositorySystemSession
    implements RepositorySystemSession
{

    private SessionData data = new TestSessionData();

    private TransferListener listener;

    private RepositoryListener repositoryListener;

    private AuthenticationSelector authenticator = new TestAuthenticationSelector();

    private ProxySelector proxySelector = new TestProxySelector();

    private LocalRepositoryManager localRepositoryManager;

    private ResolutionErrorPolicy resolutionErrorPolicy = new TestResolutionErrorPolicy();

    private boolean transferErrorCaching;

    private boolean notFoundCaching;

    private DependencyManager dependencyManager;

    private Map<String, Object> configProperties = new HashMap<String, Object>();

    private boolean offline;

    private String updatePolicy = RepositoryPolicy.UPDATE_POLICY_ALWAYS;

    private String checksumPolicy = RepositoryPolicy.CHECKSUM_POLICY_FAIL;

    private MirrorSelector mirrorSelector;

    public TestRepositorySystemSession()
        throws IOException
    {
        localRepositoryManager = new TestLocalRepositoryManager();
    }

    public TransferListener getTransferListener()
    {
        return listener;
    }

    public Map<String, Object> getConfigProperties()
    {
        return Collections.unmodifiableMap( configProperties );
    }

    public void setConfigProperties( Map<String, Object> configProperties )
    {
        if ( configProperties == null )
        {
            this.configProperties = Collections.emptyMap();
        }
        else
        {
            this.configProperties = configProperties;
        }
    }

    public boolean isOffline()
    {
        return offline;
    }

    public ResolutionErrorPolicy getResolutionErrorPolicy()
    {
        return resolutionErrorPolicy;
    }

    public boolean isIgnoreMissingArtifactDescriptor()
    {
        return false;
    }

    public boolean isIgnoreInvalidArtifactDescriptor()
    {
        return false;
    }

    public boolean isIgnoreArtifactDescriptorRepositories()
    {
        return false;
    }

    public String getChecksumPolicy()
    {
        return checksumPolicy;
    }

    public void setChecksumPolicy( String checksumPolicy )
    {
        this.checksumPolicy = checksumPolicy;
    }

    public String getUpdatePolicy()
    {
        return updatePolicy;
    }

    public void setUpdatePolicy( String updatePolicy )
    {
        this.updatePolicy = updatePolicy;
    }

    public LocalRepository getLocalRepository()
    {
        return this.localRepositoryManager.getRepository();
    }

    public LocalRepositoryManager getLocalRepositoryManager()
    {
        return localRepositoryManager;
    }

    public WorkspaceReader getWorkspaceReader()
    {
        // throw new UnsupportedOperationException( "getWorkspaceReader()" );
        return null;
    }

    public RepositoryListener getRepositoryListener()
    {
        return repositoryListener;
    }

    public Map<String, String> getSystemProperties()
    {
        return Collections.emptyMap();
    }

    public Map<String, String> getUserProperties()
    {
        return Collections.emptyMap();
    }

    public MirrorSelector getMirrorSelector()
    {
        return mirrorSelector;
    }

    public void setMirrorSelector( MirrorSelector mirrorSelector )
    {
        this.mirrorSelector = mirrorSelector;
    }

    public ProxySelector getProxySelector()
    {
        return proxySelector;
    }

    public void setProxySelector( ProxySelector proxySelector )
    {
        this.proxySelector = proxySelector;
    }

    public AuthenticationSelector getAuthenticationSelector()
    {
        return authenticator;
    }

    public ArtifactTypeRegistry getArtifactTypeRegistry()
    {
        return null;
    }

    public DependencyTraverser getDependencyTraverser()
    {
        return new DependencyTraverser()
        {

            public boolean traverseDependency( Dependency dependency )
            {
                return true;
            }

            public DependencyTraverser deriveChildTraverser( DependencyCollectionContext context )
            {
                return this;
            }
        };
    }

    public DependencyManager getDependencyManager()
    {
        if ( dependencyManager == null )
        {
            return new DependencyManager()
            {

                public DependencyManagement manageDependency( Dependency dependency )
                {
                    return null;
                }

                public DependencyManager deriveChildManager( DependencyCollectionContext context )
                {
                    return this;
                }
            };
        }
        else
        {
            return dependencyManager;
        }
    }

    public DependencySelector getDependencySelector()
    {
        return new DependencySelector()
        {

            public boolean selectDependency( Dependency dependency )
            {
                return true;
            }

            public DependencySelector deriveChildSelector( DependencyCollectionContext context )
            {
                return this;
            }
        };
    }

    public DependencyGraphTransformer getDependencyGraphTransformer()
    {
        return new DependencyGraphTransformer()
        {

            public DependencyNode transformGraph( DependencyNode node, DependencyGraphTransformationContext context )
                throws RepositoryException
            {
                return node;
            }
        };
    }

    public SessionData getData()
    {
        return data;
    }

    public RepositoryCache getCache()
    {
        return null;
    }

    public void setRepositoryListener( RepositoryListener repositoryListener )
    {
        this.repositoryListener = repositoryListener;
    }

    public void setTransferListener( TransferListener listener )
    {
        this.listener = listener;
    }

    /**
     * @param b
     */
    public void setTransferErrorCachingEnabled( boolean b )
    {
        this.transferErrorCaching = b;

    }

    /**
     * @param b
     */
    public void setNotFoundCachingEnabled( boolean b )
    {
        this.notFoundCaching = b;
    }

    public void setLocalRepositoryManager( LocalRepositoryManager localRepositoryManager )
    {
        this.localRepositoryManager = localRepositoryManager;
    }

    /**
     * @param manager
     */
    public void setDependencyManager( DependencyManager manager )
    {
        this.dependencyManager = manager;

    }

    public void setOffline( boolean offline )
    {
        this.offline = offline;
    }

    private class TestResolutionErrorPolicy
        implements ResolutionErrorPolicy
    {

        public int getArtifactPolicy( RepositorySystemSession session, ResolutionErrorPolicyRequest<Artifact> request )
        {
            return getPolicy();
        }

        public int getMetadataPolicy( RepositorySystemSession session, ResolutionErrorPolicyRequest<Metadata> request )
        {
            return getPolicy();
        }

        private int getPolicy()
        {
            return ( notFoundCaching ? ResolutionErrorPolicy.CACHE_NOT_FOUND : 0 )
                | ( transferErrorCaching ? ResolutionErrorPolicy.CACHE_TRANSFER_ERROR : 0 );
        }

    }

}
