package org.apache.maven.resolver.internal.impl;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import static java.util.Objects.requireNonNull;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.resolver.RepositoryCache;
import org.apache.maven.resolver.RepositorySystemSession;
import org.apache.maven.resolver.impl.RemoteRepositoryManager;
import org.apache.maven.resolver.impl.UpdatePolicyAnalyzer;
import org.apache.maven.resolver.repository.Authentication;
import org.apache.maven.resolver.repository.AuthenticationSelector;
import org.apache.maven.resolver.repository.MirrorSelector;
import org.apache.maven.resolver.repository.Proxy;
import org.apache.maven.resolver.repository.ProxySelector;
import org.apache.maven.resolver.repository.RemoteRepository;
import org.apache.maven.resolver.repository.RepositoryPolicy;
import org.apache.maven.resolver.spi.connector.checksum.ChecksumPolicyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
@Singleton
@Named
public class DefaultRemoteRepositoryManager
    implements RemoteRepositoryManager
{

    private static final class LoggedMirror
    {

        private final Object[] keys;

        LoggedMirror( RemoteRepository original, RemoteRepository mirror )
        {
            keys = new Object[] { mirror.getId(), mirror.getUrl(), original.getId(), original.getUrl() };
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( this == obj )
            {
                return true;
            }
            else if ( !( obj instanceof LoggedMirror ) )
            {
                return false;
            }
            LoggedMirror that = (LoggedMirror) obj;
            return Arrays.equals( keys, that.keys );
        }

        @Override
        public int hashCode()
        {
            return Arrays.hashCode( keys );
        }

    }

    private static final Logger LOGGER = LoggerFactory.getLogger( DefaultRemoteRepositoryManager.class );

    private UpdatePolicyAnalyzer updatePolicyAnalyzer;

    private ChecksumPolicyProvider checksumPolicyProvider;

    public DefaultRemoteRepositoryManager()
    {
        // enables default constructor
    }

    @Inject
    DefaultRemoteRepositoryManager( UpdatePolicyAnalyzer updatePolicyAnalyzer,
                                    ChecksumPolicyProvider checksumPolicyProvider )
    {
        setUpdatePolicyAnalyzer( updatePolicyAnalyzer );
        setChecksumPolicyProvider( checksumPolicyProvider );
    }

    public DefaultRemoteRepositoryManager setUpdatePolicyAnalyzer( UpdatePolicyAnalyzer updatePolicyAnalyzer )
    {
        this.updatePolicyAnalyzer = requireNonNull( updatePolicyAnalyzer, "update policy analyzer cannot be null" );
        return this;
    }

    public DefaultRemoteRepositoryManager setChecksumPolicyProvider( ChecksumPolicyProvider checksumPolicyProvider )
    {
        this.checksumPolicyProvider = requireNonNull(
                checksumPolicyProvider, "checksum policy provider cannot be null" );
        return this;
    }

    public List<RemoteRepository> aggregateRepositories( RepositorySystemSession session,
                                                         List<RemoteRepository> dominantRepositories,
                                                         List<RemoteRepository> recessiveRepositories,
                                                         boolean recessiveIsRaw )
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( dominantRepositories, "dominantRepositories cannot be null" );
        requireNonNull( recessiveRepositories, "recessiveRepositories cannot be null" );
        if ( recessiveRepositories.isEmpty() )
        {
            return dominantRepositories;
        }

        MirrorSelector mirrorSelector = session.getMirrorSelector();
        AuthenticationSelector authSelector = session.getAuthenticationSelector();
        ProxySelector proxySelector = session.getProxySelector();

        List<RemoteRepository> result = new ArrayList<>( dominantRepositories );

        next: for ( RemoteRepository recessiveRepository : recessiveRepositories )
        {
            RemoteRepository repository = recessiveRepository;

            if ( recessiveIsRaw )
            {
                RemoteRepository mirrorRepository = mirrorSelector.getMirror( recessiveRepository );

                if ( mirrorRepository != null )
                {
                    logMirror( session, recessiveRepository, mirrorRepository );
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
                RemoteRepository.Builder builder = null;
                Authentication auth = authSelector.getAuthentication( repository );
                if ( auth != null )
                {
                    builder = new RemoteRepository.Builder( repository );
                    builder.setAuthentication( auth );
                }
                Proxy proxy = proxySelector.getProxy( repository );
                if ( proxy != null )
                {
                    if ( builder == null )
                    {
                        builder = new RemoteRepository.Builder( repository );
                    }
                    builder.setProxy( proxy );
                }
                if ( builder != null )
                {
                    repository = builder.build();
                }
            }

            result.add( repository );
        }

        return result;
    }

    private void logMirror( RepositorySystemSession session, RemoteRepository original, RemoteRepository mirror )
    {
        if ( !LOGGER.isDebugEnabled() )
        {
            return;
        }
        RepositoryCache cache = session.getCache();
        if ( cache != null )
        {
            Object key = new LoggedMirror( original, mirror );
            if ( cache.get( session, key ) != null )
            {
                return;
            }
            cache.put( session, key, Boolean.TRUE );
        }
        LOGGER.debug( "Using mirror {} ({}) for {} ({}).",
                mirror.getId(), mirror.getUrl(), original.getId(), original.getUrl() );
    }

    private String getKey( RemoteRepository repository )
    {
        return repository.getId();
    }

    private RemoteRepository mergeMirrors( RepositorySystemSession session, RemoteRepository dominant,
                                           RemoteRepository recessive )
    {
        RemoteRepository.Builder merged = null;
        RepositoryPolicy releases = null, snapshots = null;

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

            if ( merged == null )
            {
                merged = new RemoteRepository.Builder( dominant );
                releases = dominant.getPolicy( false );
                snapshots = dominant.getPolicy( true );
            }

            releases = merge( session, releases, rec.getPolicy( false ), false );
            snapshots = merge( session, snapshots, rec.getPolicy( true ), false );

            merged.addMirroredRepository( rec );
        }

        if ( merged == null )
        {
            return dominant;
        }
        return merged.setReleasePolicy( releases ).setSnapshotPolicy( snapshots ).build();
    }

    public RepositoryPolicy getPolicy( RepositorySystemSession session, RemoteRepository repository, boolean releases,
                                       boolean snapshots )
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( repository, "repository cannot be null" );
        RepositoryPolicy policy1 = releases ? repository.getPolicy( false ) : null;
        RepositoryPolicy policy2 = snapshots ? repository.getPolicy( true ) : null;
        return merge( session, policy1, policy2, true );
    }

    private RepositoryPolicy merge( RepositorySystemSession session, RepositoryPolicy policy1,
                                    RepositoryPolicy policy2, boolean globalPolicy )
    {
        RepositoryPolicy policy;

        if ( policy2 == null )
        {
            if ( globalPolicy )
            {
                policy = merge( policy1, session.getUpdatePolicy(), session.getChecksumPolicy() );
            }
            else
            {
                policy = policy1;
            }
        }
        else if ( policy1 == null )
        {
            if ( globalPolicy )
            {
                policy = merge( policy2, session.getUpdatePolicy(), session.getChecksumPolicy() );
            }
            else
            {
                policy = policy2;
            }
        }
        else if ( !policy2.isEnabled() )
        {
            if ( globalPolicy )
            {
                policy = merge( policy1, session.getUpdatePolicy(), session.getChecksumPolicy() );
            }
            else
            {
                policy = policy1;
            }
        }
        else if ( !policy1.isEnabled() )
        {
            if ( globalPolicy )
            {
                policy = merge( policy2, session.getUpdatePolicy(), session.getChecksumPolicy() );
            }
            else
            {
                policy = policy2;
            }
        }
        else
        {
            String checksums = session.getChecksumPolicy();
            //noinspection StatementWithEmptyBody
            if ( globalPolicy && !StringUtils.isEmpty( checksums ) )
            {
                // use global override
            }
            else
            {
                checksums =
                    checksumPolicyProvider.getEffectiveChecksumPolicy( session, policy1.getChecksumPolicy(),
                                                                       policy2.getChecksumPolicy() );
            }

            String updates = session.getUpdatePolicy();
            //noinspection StatementWithEmptyBody
            if ( globalPolicy && !StringUtils.isEmpty( updates ) )
            {
                // use global override
            }
            else
            {
                updates =
                    updatePolicyAnalyzer.getEffectiveUpdatePolicy( session, policy1.getUpdatePolicy(),
                                                                   policy2.getUpdatePolicy() );
            }

            policy = new RepositoryPolicy( true, updates, checksums );
        }

        return policy;
    }

    private RepositoryPolicy merge( RepositoryPolicy policy, String updates, String checksums )
    {
        if ( policy != null )
        {
            if ( StringUtils.isEmpty( updates ) )
            {
                updates = policy.getUpdatePolicy();
            }
            if ( StringUtils.isEmpty( checksums ) )
            {
                checksums = policy.getChecksumPolicy();
            }
            if ( !policy.getUpdatePolicy().equals( updates ) || !policy.getChecksumPolicy().equals( checksums ) )
            {
                policy = new RepositoryPolicy( policy.isEnabled(), updates, checksums );
            }
        }
        return policy;
    }

}
