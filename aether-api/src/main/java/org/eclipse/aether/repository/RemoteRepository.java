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
package org.eclipse.aether.repository;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A repository on a remote server.
 */
public final class RemoteRepository
    implements ArtifactRepository
{

    private static final Pattern URL_PATTERN =
        Pattern.compile( "([^:/]+(:[^:/]{2,}+(?=://))?):(//([^@/]*@)?([^/:]+))?.*" );

    private String id = "";

    private String type = "";

    private String url = "";

    private RepositoryPolicy releasePolicy;

    private RepositoryPolicy snapshotPolicy;

    private Proxy proxy;

    private Authentication authentication;

    private List<RemoteRepository> mirroredRepositories = Collections.emptyList();

    private boolean repositoryManager;

    /**
     * Creates a new repository using the default release/snapshot policies.
     */
    public RemoteRepository()
    {
        setPolicy( true, null );
        setPolicy( false, null );
    }

    /**
     * Creates a shallow copy of the specified repository.
     * 
     * @param repository The repository to copy, must not be {@code null}.
     */
    public RemoteRepository( RemoteRepository repository )
    {
        setId( repository.getId() );
        setContentType( repository.getContentType() );
        setUrl( repository.getUrl() );
        setPolicy( true, repository.getPolicy( true ) );
        setPolicy( false, repository.getPolicy( false ) );
        setAuthentication( repository.getAuthentication() );
        setProxy( repository.getProxy() );
        setMirroredRepositories( repository.getMirroredRepositories() );
        setRepositoryManager( repository.isRepositoryManager() );
    }

    /**
     * Creates a new repository with the specified properties and the default policies.
     * 
     * @param id The identifier of the repository, may be {@code null}.
     * @param type The type of the repository, may be {@code null}.
     * @param url The (base) URL of the repository, may be {@code null}.
     */
    public RemoteRepository( String id, String type, String url )
    {
        setId( id );
        setContentType( type );
        setUrl( url );
        setPolicy( true, null );
        setPolicy( false, null );
    }

    public String getId()
    {
        return id;
    }

    /**
     * Sets the identifier of this repository.
     * 
     * @param id The identifier of this repository, may be {@code null}.
     * @return This repository for chaining, never {@code null}.
     */
    public RemoteRepository setId( String id )
    {
        this.id = ( id != null ) ? id : "";

        return this;
    }

    public String getContentType()
    {
        return type;
    }

    /**
     * Sets the type of this repository, e.g. "default".
     * 
     * @param type The type of this repository, may be {@code null}.
     * @return This repository for chaining, never {@code null}.
     */
    public RemoteRepository setContentType( String type )
    {
        this.type = ( type != null ) ? type : "";

        return this;
    }

    /**
     * Gets the (base) URL of this repository.
     * 
     * @return The (base) URL of this repository, never {@code null}.
     */
    public String getUrl()
    {
        return url;
    }

    /**
     * Sets the (base) URL of this repository.
     * 
     * @param url The URL of this repository, may be {@code null}.
     * @return This repository for chaining, never {@code null}.
     */
    public RemoteRepository setUrl( String url )
    {
        this.url = ( url != null ) ? url : "";

        return this;
    }

    /**
     * Gets the protocol part from the repository's URL, for example {@code file} or {@code http}. As suggested by RFC
     * 2396, section 3.1 "Scheme Component", the protocol name should be treated case-insensitively.
     * 
     * @return The protocol or an empty string if none, never {@code null}.
     */
    public String getProtocol()
    {
        Matcher m = URL_PATTERN.matcher( this.url );

        if ( m.matches() )
        {
            return m.group( 1 );
        }
        return "";
    }

    /**
     * Gets the host part from the repository's URL.
     * 
     * @return The host or an empty string if none, never {@code null}.
     */
    public String getHost()
    {
        Matcher m = URL_PATTERN.matcher( this.url );

        if ( m.matches() )
        {
            String host = m.group( 5 );
            if ( host != null )
            {
                return host;
            }
        }
        return "";
    }

    /**
     * Gets the policy to apply for snapshot/release artifacts.
     * 
     * @param snapshot {@code true} to retrieve the snapshot policy, {@code false} to retrieve the release policy.
     * @return The requested repository policy, never {@code null}.
     */
    public RepositoryPolicy getPolicy( boolean snapshot )
    {
        return snapshot ? snapshotPolicy : releasePolicy;
    }

    /**
     * Sets the policy to apply for snapshot/release artifacts.
     * 
     * @param snapshot {@code true} to set the snapshot policy, {@code false} to set the release policy.
     * @param policy The repository policy to set, may be {@code null} to use a default policy.
     * @return This repository for chaining, never {@code null}.
     */
    public RemoteRepository setPolicy( boolean snapshot, RepositoryPolicy policy )
    {
        if ( policy == null )
        {
            policy = new RepositoryPolicy();
        }

        if ( snapshot )
        {
            snapshotPolicy = policy;
        }
        else
        {
            releasePolicy = policy;
        }

        return this;
    }

    /**
     * Gets the proxy that has been selected for this repository.
     * 
     * @return The selected proxy or {@code null} if none.
     */
    public Proxy getProxy()
    {
        return proxy;
    }

    /**
     * Sets the proxy to use in order to access this repository.
     * 
     * @param proxy The proxy to use, may be {@code null}.
     * @return This repository for chaining, never {@code null}.
     */
    public RemoteRepository setProxy( Proxy proxy )
    {
        this.proxy = proxy;

        return this;
    }

    /**
     * Gets the authentication that has been selected for this repository.
     * 
     * @return The selected authentication or {@code null} if none.
     */
    public Authentication getAuthentication()
    {
        return authentication;
    }

    /**
     * Sets the authentication to use in order to access this repository.
     * 
     * @param authentication The authentication to use, may be {@code null}.
     * @return This repository for chaining, never {@code null}.
     */
    public RemoteRepository setAuthentication( Authentication authentication )
    {
        this.authentication = authentication;

        return this;
    }

    /**
     * Gets the repositories that this repository serves as a mirror for.
     * 
     * @return The repositories being mirrored by this repository, never {@code null}.
     */
    public List<RemoteRepository> getMirroredRepositories()
    {
        return mirroredRepositories;
    }

    /**
     * Sets the repositories being mirrored by this repository.
     * 
     * @param mirroredRepositories The repositories being mirrored by this repository, may be {@code null}.
     * @return This repository for chaining, never {@code null}.
     */
    public RemoteRepository setMirroredRepositories( List<RemoteRepository> mirroredRepositories )
    {
        if ( mirroredRepositories == null || mirroredRepositories.isEmpty() )
        {
            this.mirroredRepositories = Collections.emptyList();
        }
        else
        {
            this.mirroredRepositories = mirroredRepositories;
        }
        return this;
    }

    /**
     * Indicates whether this repository refers to a repository manager or not.
     * 
     * @return {@code true} if this repository is a repository manager, {@code false} otherwise.
     */
    public boolean isRepositoryManager()
    {
        return repositoryManager;
    }

    /**
     * Marks this repository as a repository manager or not.
     * 
     * @param repositoryManager {@code true} if this repository points at a repository manager, {@code false} if the
     *            repository is just serving static contents.
     * @return This repository for chaining, never {@code null}.
     */
    public RemoteRepository setRepositoryManager( boolean repositoryManager )
    {
        this.repositoryManager = repositoryManager;
        return this;
    }

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder( 256 );
        buffer.append( getId() );
        buffer.append( " (" ).append( getUrl() );
        boolean r = getPolicy( false ).isEnabled(), s = getPolicy( true ).isEnabled();
        if ( r && s )
        {
            buffer.append( ", releases+snapshots" );
        }
        else if ( r )
        {
            buffer.append( ", releases" );
        }
        else if ( s )
        {
            buffer.append( ", snapshots" );
        }
        else
        {
            buffer.append( ", disabled" );
        }
        if ( isRepositoryManager() )
        {
            buffer.append( ", managed" );
        }
        buffer.append( ")" );
        return buffer.toString();
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null || !getClass().equals( obj.getClass() ) )
        {
            return false;
        }

        RemoteRepository that = (RemoteRepository) obj;

        return eq( url, that.url ) && eq( type, that.type ) && eq( id, that.id )
            && eq( releasePolicy, that.releasePolicy ) && eq( snapshotPolicy, that.snapshotPolicy )
            && eq( proxy, that.proxy ) && eq( authentication, that.authentication )
            && eq( mirroredRepositories, that.mirroredRepositories ) && repositoryManager == that.repositoryManager;
    }

    private static <T> boolean eq( T s1, T s2 )
    {
        return s1 != null ? s1.equals( s2 ) : s2 == null;
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + hash( url );
        hash = hash * 31 + hash( type );
        hash = hash * 31 + hash( id );
        hash = hash * 31 + hash( releasePolicy );
        hash = hash * 31 + hash( snapshotPolicy );
        hash = hash * 31 + hash( proxy );
        hash = hash * 31 + hash( authentication );
        hash = hash * 31 + hash( mirroredRepositories );
        hash = hash * 31 + ( repositoryManager ? 1 : 0 );
        return hash;
    }

    private static int hash( Object obj )
    {
        return obj != null ? obj.hashCode() : 0;
    }

}
