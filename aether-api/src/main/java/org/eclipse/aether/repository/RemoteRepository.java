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
package org.eclipse.aether.repository;

import java.util.ArrayList;
import java.util.Arrays;
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

    private final String id;

    private final String type;

    private final String url;

    private final String host;

    private final String protocol;

    private final RepositoryPolicy releasePolicy;

    private final RepositoryPolicy snapshotPolicy;

    private final Proxy proxy;

    private final Authentication authentication;

    private final List<RemoteRepository> mirroredRepositories;

    private final boolean repositoryManager;

    RemoteRepository( Builder builder )
    {
        if ( builder.prototype != null )
        {
            id = ( builder.delta & Builder.ID ) != 0 ? builder.id : builder.prototype.id;
            type = ( builder.delta & Builder.TYPE ) != 0 ? builder.type : builder.prototype.type;
            url = ( builder.delta & Builder.URL ) != 0 ? builder.url : builder.prototype.url;
            releasePolicy =
                ( builder.delta & Builder.RELEASES ) != 0 ? builder.releasePolicy : builder.prototype.releasePolicy;
            snapshotPolicy =
                ( builder.delta & Builder.SNAPSHOTS ) != 0 ? builder.snapshotPolicy : builder.prototype.snapshotPolicy;
            proxy = ( builder.delta & Builder.PROXY ) != 0 ? builder.proxy : builder.prototype.proxy;
            authentication =
                ( builder.delta & Builder.AUTH ) != 0 ? builder.authentication : builder.prototype.authentication;
            repositoryManager =
                ( builder.delta & Builder.REPOMAN ) != 0 ? builder.repositoryManager
                                : builder.prototype.repositoryManager;
            mirroredRepositories =
                ( builder.delta & Builder.MIRRORED ) != 0 ? copy( builder.mirroredRepositories )
                                : builder.prototype.mirroredRepositories;
        }
        else
        {
            id = builder.id;
            type = builder.type;
            url = builder.url;
            releasePolicy = builder.releasePolicy;
            snapshotPolicy = builder.snapshotPolicy;
            proxy = builder.proxy;
            authentication = builder.authentication;
            repositoryManager = builder.repositoryManager;
            mirroredRepositories = copy( builder.mirroredRepositories );
        }

        Matcher m = URL_PATTERN.matcher( url );
        if ( m.matches() )
        {
            protocol = m.group( 1 );
            String host = m.group( 5 );
            this.host = ( host != null ) ? host : "";
        }
        else
        {
            protocol = host = "";
        }
    }

    private static List<RemoteRepository> copy( List<RemoteRepository> repos )
    {
        if ( repos == null || repos.isEmpty() )
        {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList( Arrays.asList( repos.toArray( new RemoteRepository[repos.size()] ) ) );
    }

    public String getId()
    {
        return id;
    }

    public String getContentType()
    {
        return type;
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
     * Gets the protocol part from the repository's URL, for example {@code file} or {@code http}. As suggested by RFC
     * 2396, section 3.1 "Scheme Component", the protocol name should be treated case-insensitively.
     * 
     * @return The protocol or an empty string if none, never {@code null}.
     */
    public String getProtocol()
    {
        return protocol;
    }

    /**
     * Gets the host part from the repository's URL.
     * 
     * @return The host or an empty string if none, never {@code null}.
     */
    public String getHost()
    {
        return host;
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
     * Gets the proxy that has been selected for this repository.
     * 
     * @return The selected proxy or {@code null} if none.
     */
    public Proxy getProxy()
    {
        return proxy;
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
     * Gets the repositories that this repository serves as a mirror for.
     * 
     * @return The (read-only) repositories being mirrored by this repository, never {@code null}.
     */
    public List<RemoteRepository> getMirroredRepositories()
    {
        return mirroredRepositories;
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

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder( 256 );
        buffer.append( getId() );
        buffer.append( " (" ).append( getUrl() );
        buffer.append( ", " ).append( getContentType() );
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

    /**
     * A builder to create remote repositories.
     */
    public static final class Builder
    {

        private static final RepositoryPolicy DEFAULT_POLICY = new RepositoryPolicy();

        static final int ID = 0x0001, TYPE = 0x0002, URL = 0x0004, RELEASES = 0x0008, SNAPSHOTS = 0x0010,
                        PROXY = 0x0020, AUTH = 0x0040, MIRRORED = 0x0080, REPOMAN = 0x0100;

        int delta;

        RemoteRepository prototype;

        String id;

        String type;

        String url;

        RepositoryPolicy releasePolicy = DEFAULT_POLICY;

        RepositoryPolicy snapshotPolicy = DEFAULT_POLICY;

        Proxy proxy;

        Authentication authentication;

        List<RemoteRepository> mirroredRepositories;

        boolean repositoryManager;

        /**
         * Creates a new repository builder.
         * 
         * @param id The identifier of the repository, may be {@code null}.
         * @param type The type of the repository, may be {@code null}.
         * @param url The (base) URL of the repository, may be {@code null}.
         */
        public Builder( String id, String type, String url )
        {
            this.id = ( id != null ) ? id : "";
            this.type = ( type != null ) ? type : "";
            this.url = ( url != null ) ? url : "";
        }

        /**
         * Creates a new repository builder which uses the specified remote repository as a prototype for the new one.
         * All properties which have not been set on the builder will be copied from the prototype when building the
         * repository.
         * 
         * @param prototype The remote repository to use as prototype, must not be {@code null}.
         */
        public Builder( RemoteRepository prototype )
        {
            if ( prototype == null )
            {
                throw new IllegalArgumentException( "repository prototype missing" );
            }
            this.prototype = prototype;
        }

        /**
         * Builds a new remote repository from the current values of this builder. The state of the builder itself
         * remains unchanged.
         * 
         * @return The remote repository, never {@code null}.
         */
        public RemoteRepository build()
        {
            if ( prototype != null && delta == 0 )
            {
                return prototype;
            }
            return new RemoteRepository( this );
        }

        private <T> void delta( int flag, T builder, T prototype )
        {
            boolean equal = ( builder != null ) ? builder.equals( prototype ) : prototype == null;
            if ( equal )
            {
                delta &= ~flag;
            }
            else
            {
                delta |= flag;
            }
        }

        /**
         * Sets the identifier of the repository.
         * 
         * @param id The identifier of the repository, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setId( String id )
        {
            this.id = ( id != null ) ? id : "";
            if ( prototype != null )
            {
                delta( ID, this.id, prototype.getId() );
            }
            return this;
        }

        /**
         * Sets the type of the repository, e.g. "default".
         * 
         * @param type The type of the repository, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setContentType( String type )
        {
            this.type = ( type != null ) ? type : "";
            if ( prototype != null )
            {
                delta( TYPE, this.type, prototype.getContentType() );
            }
            return this;
        }

        /**
         * Sets the (base) URL of the repository.
         * 
         * @param url The URL of the repository, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setUrl( String url )
        {
            this.url = ( url != null ) ? url : "";
            if ( prototype != null )
            {
                delta( URL, this.url, prototype.getUrl() );
            }
            return this;
        }

        /**
         * Sets the policy to apply for snapshot and release artifacts.
         * 
         * @param policy The repository policy to set, may be {@code null} to use a default policy.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setPolicy( RepositoryPolicy policy )
        {
            this.releasePolicy = this.snapshotPolicy = ( policy != null ) ? policy : DEFAULT_POLICY;
            if ( prototype != null )
            {
                delta( RELEASES, this.releasePolicy, prototype.getPolicy( false ) );
                delta( SNAPSHOTS, this.snapshotPolicy, prototype.getPolicy( true ) );
            }
            return this;
        }

        /**
         * Sets the policy to apply for release artifacts.
         * 
         * @param releasePolicy The repository policy to set, may be {@code null} to use a default policy.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setReleasePolicy( RepositoryPolicy releasePolicy )
        {
            this.releasePolicy = ( releasePolicy != null ) ? releasePolicy : DEFAULT_POLICY;
            if ( prototype != null )
            {
                delta( RELEASES, this.releasePolicy, prototype.getPolicy( false ) );
            }
            return this;
        }

        /**
         * Sets the policy to apply for snapshot artifacts.
         * 
         * @param snapshotPolicy The repository policy to set, may be {@code null} to use a default policy.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setSnapshotPolicy( RepositoryPolicy snapshotPolicy )
        {
            this.snapshotPolicy = ( snapshotPolicy != null ) ? snapshotPolicy : DEFAULT_POLICY;
            if ( prototype != null )
            {
                delta( SNAPSHOTS, this.snapshotPolicy, prototype.getPolicy( true ) );
            }
            return this;
        }

        /**
         * Sets the proxy to use in order to access the repository.
         * 
         * @param proxy The proxy to use, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setProxy( Proxy proxy )
        {
            this.proxy = proxy;
            if ( prototype != null )
            {
                delta( PROXY, this.proxy, prototype.getProxy() );
            }
            return this;
        }

        /**
         * Sets the authentication to use in order to access the repository.
         * 
         * @param authentication The authentication to use, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setAuthentication( Authentication authentication )
        {
            this.authentication = authentication;
            if ( prototype != null )
            {
                delta( AUTH, this.authentication, prototype.getAuthentication() );
            }
            return this;
        }

        /**
         * Sets the repositories being mirrored by the repository.
         * 
         * @param mirroredRepositories The repositories being mirrored by the repository, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setMirroredRepositories( List<RemoteRepository> mirroredRepositories )
        {
            if ( this.mirroredRepositories == null )
            {
                this.mirroredRepositories = new ArrayList<RemoteRepository>();
            }
            else
            {
                this.mirroredRepositories.clear();
            }
            if ( mirroredRepositories != null )
            {
                this.mirroredRepositories.addAll( mirroredRepositories );
            }
            if ( prototype != null )
            {
                delta( MIRRORED, this.mirroredRepositories, prototype.getMirroredRepositories() );
            }
            return this;
        }

        /**
         * Adds the specified repository to the list of repositories being mirrored by the repository. If this builder
         * was {@link #RemoteRepository.Builder(RemoteRepository) constructed from a prototype}, the given repository
         * will be added to the list of mirrored repositories from the prototype.
         * 
         * @param mirroredRepository The repository being mirrored by the repository, may be {@code null}.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder addMirroredRepository( RemoteRepository mirroredRepository )
        {
            if ( mirroredRepository != null )
            {
                if ( this.mirroredRepositories == null )
                {
                    this.mirroredRepositories = new ArrayList<RemoteRepository>();
                    if ( prototype != null )
                    {
                        mirroredRepositories.addAll( prototype.getMirroredRepositories() );
                    }
                }
                mirroredRepositories.add( mirroredRepository );
                if ( prototype != null )
                {
                    delta |= MIRRORED;
                }
            }
            return this;
        }

        /**
         * Marks the repository as a repository manager or not.
         * 
         * @param repositoryManager {@code true} if the repository points at a repository manager, {@code false} if the
         *            repository is just serving static contents.
         * @return This builder for chaining, never {@code null}.
         */
        public Builder setRepositoryManager( boolean repositoryManager )
        {
            this.repositoryManager = repositoryManager;
            if ( prototype != null )
            {
                delta( REPOMAN, this.repositoryManager, prototype.isRepositoryManager() );
            }
            return this;
        }

    }

}
