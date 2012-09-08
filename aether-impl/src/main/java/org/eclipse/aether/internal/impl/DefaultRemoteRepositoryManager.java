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
package org.eclipse.aether.internal.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.UpdatePolicyAnalyzer;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.util.StringUtils;

/**
 */
@Named
@Component( role = RemoteRepositoryManager.class )
public class DefaultRemoteRepositoryManager
    implements RemoteRepositoryManager, Service
{

    @Requirement( role = LoggerFactory.class )
    private Logger logger = NullLoggerFactory.LOGGER;

    @Requirement
    private UpdatePolicyAnalyzer updatePolicyAnalyzer;

    public DefaultRemoteRepositoryManager()
    {
        // enables default constructor
    }

    @Inject
    DefaultRemoteRepositoryManager( UpdatePolicyAnalyzer updatePolicyAnalyzer, LoggerFactory loggerFactory )
    {
        setUpdatePolicyAnalyzer( updatePolicyAnalyzer );
        setLoggerFactory( loggerFactory );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        setUpdatePolicyAnalyzer( locator.getService( UpdatePolicyAnalyzer.class ) );
    }

    public DefaultRemoteRepositoryManager setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    void setLogger( LoggerFactory loggerFactory )
    {
        // plexus support
        setLoggerFactory( loggerFactory );
    }

    public DefaultRemoteRepositoryManager setUpdatePolicyAnalyzer( UpdatePolicyAnalyzer updatePolicyAnalyzer )
    {
        if ( updatePolicyAnalyzer == null )
        {
            throw new IllegalArgumentException( "update policy analyzer has not been specified" );
        }
        this.updatePolicyAnalyzer = updatePolicyAnalyzer;
        return this;
    }

    public List<RemoteRepository> aggregateRepositories( RepositorySystemSession session,
                                                         List<RemoteRepository> dominantRepositories,
                                                         List<RemoteRepository> recessiveRepositories,
                                                         boolean recessiveIsRaw )
    {
        if ( recessiveRepositories.isEmpty() )
        {
            return dominantRepositories;
        }

        MirrorSelector mirrorSelector = session.getMirrorSelector();
        AuthenticationSelector authSelector = session.getAuthenticationSelector();
        ProxySelector proxySelector = session.getProxySelector();

        List<RemoteRepository> result = new ArrayList<RemoteRepository>( dominantRepositories );

        next: for ( RemoteRepository recessiveRepository : recessiveRepositories )
        {
            RemoteRepository repository = recessiveRepository;

            if ( recessiveIsRaw )
            {
                RemoteRepository mirrorRepository = mirrorSelector.getMirror( recessiveRepository );

                if ( mirrorRepository == null )
                {
                    repository = recessiveRepository;
                }
                else
                {
                    logger.debug( "Using mirror " + mirrorRepository.getId() + " (" + mirrorRepository.getUrl()
                        + ") for " + recessiveRepository.getId() + " (" + recessiveRepository.getUrl() + ")." );
                    repository = mirrorRepository;
                }
            }

            String key = getKey( repository );

            for ( ListIterator<RemoteRepository> it = result.listIterator(); it.hasNext(); )
            {
                RemoteRepository dominantRepository = it.next();

                if ( key.equals( getKey( dominantRepository ) ) )
                {
                    if ( !dominantRepository.getMirroredRepositories().isEmpty()
                        && !repository.getMirroredRepositories().isEmpty() )
                    {
                        RemoteRepository mergedRepository = mergeMirrors( session, dominantRepository, repository );
                        if ( mergedRepository != dominantRepository )
                        {
                            it.set( mergedRepository );
                        }
                    }

                    continue next;
                }
            }

            if ( recessiveIsRaw )
            {
                Authentication auth = authSelector.getAuthentication( repository );
                if ( auth != null )
                {
                    repository.setAuthentication( auth );
                }
                Proxy proxy = proxySelector.getProxy( repository );
                if ( proxy != null )
                {
                    repository.setProxy( proxy );
                }
            }

            result.add( repository );
        }

        return result;
    }

    private String getKey( RemoteRepository repository )
    {
        return repository.getId();
    }

    private RemoteRepository mergeMirrors( RepositorySystemSession session, RemoteRepository dominant,
                                           RemoteRepository recessive )
    {
        RemoteRepository merged = dominant;

        next: for ( RemoteRepository rec : recessive.getMirroredRepositories() )
        {
            String recKey = getKey( rec );

            for ( RemoteRepository dom : dominant.getMirroredRepositories() )
            {
                if ( recKey.equals( getKey( dom ) ) )
                {
                    continue next;
                }
            }

            if ( merged == dominant )
            {
                merged = new RemoteRepository();

                merged.setRepositoryManager( dominant.isRepositoryManager() );

                merged.setId( dominant.getId() );
                merged.setContentType( dominant.getContentType() );
                merged.setUrl( dominant.getUrl() );

                merged.setAuthentication( dominant.getAuthentication() );
                merged.setProxy( dominant.getProxy() );

                merged.setPolicy( false, dominant.getPolicy( false ) );
                merged.setPolicy( true, dominant.getPolicy( true ) );

                merged.setMirroredRepositories( new ArrayList<RemoteRepository>( dominant.getMirroredRepositories() ) );
            }

            merged.setPolicy( false, merge( session, merged.getPolicy( false ), rec.getPolicy( false ) ) );
            merged.setPolicy( true, merge( session, merged.getPolicy( true ), rec.getPolicy( true ) ) );

            merged.getMirroredRepositories().add( rec );
        }

        return merged;
    }

    public RepositoryPolicy getPolicy( RepositorySystemSession session, RemoteRepository repository, boolean releases,
                                       boolean snapshots )
    {
        RepositoryPolicy policy;

        // get effective per-repository policy
        if ( releases && snapshots )
        {
            policy = merge( session, repository.getPolicy( false ), repository.getPolicy( true ) );
        }
        else
        {
            policy = repository.getPolicy( snapshots );
        }

        // superimpose global policy
        if ( !StringUtils.isEmpty( session.getChecksumPolicy() ) )
        {
            policy = policy.setChecksumPolicy( session.getChecksumPolicy() );
        }
        if ( !StringUtils.isEmpty( session.getUpdatePolicy() ) )
        {
            policy = policy.setUpdatePolicy( session.getUpdatePolicy() );
        }

        return policy;
    }

    private RepositoryPolicy merge( RepositorySystemSession session, RepositoryPolicy policy1, RepositoryPolicy policy2 )
    {
        RepositoryPolicy policy;

        if ( policy1.isEnabled() && policy2.isEnabled() )
        {
            String checksums;
            if ( ordinalOfChecksumPolicy( policy2.getChecksumPolicy() ) < ordinalOfChecksumPolicy( policy1.getChecksumPolicy() ) )
            {
                checksums = policy2.getChecksumPolicy();
            }
            else
            {
                checksums = policy1.getChecksumPolicy();
            }

            String updates =
                updatePolicyAnalyzer.getEffectiveUpdatePolicy( session, policy1.getUpdatePolicy(),
                                                               policy2.getUpdatePolicy() );

            policy = new RepositoryPolicy( true, updates, checksums );
        }
        else if ( policy2.isEnabled() )
        {
            policy = policy2;
        }
        else
        {
            policy = policy1;
        }

        return policy;
    }

    private int ordinalOfChecksumPolicy( String policy )
    {
        if ( RepositoryPolicy.CHECKSUM_POLICY_FAIL.equals( policy ) )
        {
            return 2;
        }
        else if ( RepositoryPolicy.CHECKSUM_POLICY_IGNORE.equals( policy ) )
        {
            return 0;
        }
        else
        {
            return 1;
        }
    }

}
