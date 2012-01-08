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
package org.eclipse.aether;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.artifact.ArtifactType;
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
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.transfer.TransferListener;

/**
 * A simple repository system session. <em>Note:</em> This class is not thread-safe. It is assumed that the mutators get
 * only called during an initialization phase and that the session itself is not changed when being used by the
 * repository system.
 */
public final class DefaultRepositorySystemSession
    implements RepositorySystemSession
{

    private boolean readOnly;

    private boolean offline;

    private boolean transferErrorCachingEnabled;

    private boolean notFoundCachingEnabled;

    private boolean ignoreMissingArtifactDescriptor;

    private boolean ignoreInvalidArtifactDescriptor;

    private boolean ignoreArtifactDescriptorRepositories;

    private String checksumPolicy;

    private String updatePolicy;

    private LocalRepositoryManager localRepositoryManager;

    private WorkspaceReader workspaceReader;

    private RepositoryListener repositoryListener;

    private TransferListener transferListener;

    private Map<String, String> systemProperties;

    private Map<String, String> systemPropertiesView;

    private Map<String, String> userProperties;

    private Map<String, String> userPropertiesView;

    private Map<String, Object> configProperties;

    private Map<String, Object> configPropertiesView;

    private MirrorSelector mirrorSelector;

    private ProxySelector proxySelector;

    private AuthenticationSelector authenticationSelector;

    private ArtifactTypeRegistry artifactTypeRegistry;

    private DependencyTraverser dependencyTraverser;

    private DependencyManager dependencyManager;

    private DependencySelector dependencySelector;

    private DependencyGraphTransformer dependencyGraphTransformer;

    private SessionData data;

    private RepositoryCache cache;

    /**
     * Creates an uninitialized session. <em>Note:</em> The new session is not ready to use, as a bare minimum,
     * {@link #setLocalRepositoryManager(LocalRepositoryManager)} needs to be called but usually other settings also
     * need to be customized to achieve meaningful behavior.
     */
    public DefaultRepositorySystemSession()
    {
        systemProperties = new HashMap<String, String>();
        systemPropertiesView = Collections.unmodifiableMap( systemProperties );
        userProperties = new HashMap<String, String>();
        userPropertiesView = Collections.unmodifiableMap( userProperties );
        configProperties = new HashMap<String, Object>();
        configPropertiesView = Collections.unmodifiableMap( configProperties );
        mirrorSelector = NullMirrorSelector.INSTANCE;
        proxySelector = NullProxySelector.INSTANCE;
        authenticationSelector = NullAuthenticationSelector.INSTANCE;
        artifactTypeRegistry = NullArtifactTypeRegistry.INSTANCE;
        dependencyTraverser = NullDependencyTraverser.INSTANCE;
        dependencyManager = NullDependencyManager.INSTANCE;
        dependencySelector = NullDependencySelector.INSTANCE;
        dependencyGraphTransformer = NullDependencyGraphTransformer.INSTANCE;
        data = new DefaultSessionData();
    }

    /**
     * Creates a shallow copy of the specified session.
     * 
     * @param session The session to copy, must not be {@code null}.
     */
    public DefaultRepositorySystemSession( RepositorySystemSession session )
    {
        if ( session == null )
        {
            throw new IllegalArgumentException( "repository system session not specified" );
        }

        setOffline( session.isOffline() );
        setTransferErrorCachingEnabled( session.isTransferErrorCachingEnabled() );
        setNotFoundCachingEnabled( session.isNotFoundCachingEnabled() );
        setIgnoreInvalidArtifactDescriptor( session.isIgnoreInvalidArtifactDescriptor() );
        setIgnoreMissingArtifactDescriptor( session.isIgnoreMissingArtifactDescriptor() );
        setIgnoreArtifactDescriptorRepositories( session.isIgnoreArtifactDescriptorRepositories() );
        setChecksumPolicy( session.getChecksumPolicy() );
        setUpdatePolicy( session.getUpdatePolicy() );
        setLocalRepositoryManager( session.getLocalRepositoryManager() );
        setWorkspaceReader( session.getWorkspaceReader() );
        setRepositoryListener( session.getRepositoryListener() );
        setTransferListener( session.getTransferListener() );
        setSystemProperties( session.getSystemProperties() );
        setUserProperties( session.getUserProperties() );
        setConfigProperties( session.getConfigProperties() );
        setMirrorSelector( session.getMirrorSelector() );
        setProxySelector( session.getProxySelector() );
        setAuthenticationSelector( session.getAuthenticationSelector() );
        setArtifactTypeRegistry( session.getArtifactTypeRegistry() );
        setDependencyTraverser( session.getDependencyTraverser() );
        setDependencyManager( session.getDependencyManager() );
        setDependencySelector( session.getDependencySelector() );
        setDependencyGraphTransformer( session.getDependencyGraphTransformer() );
        setData( session.getData() );
        setCache( session.getCache() );
    }

    public boolean isOffline()
    {
        return offline;
    }

    /**
     * Controls whether the repository system operates in offline mode and avoids/refuses any access to remote
     * repositories.
     * 
     * @param offline {@code true} if the repository system is in offline mode, {@code false} otherwise.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setOffline( boolean offline )
    {
        failIfReadOnly();
        this.offline = offline;
        return this;
    }

    public boolean isTransferErrorCachingEnabled()
    {
        return transferErrorCachingEnabled;
    }

    /**
     * Controls whether transfer errors (e.g. unreachable host, bad authentication) from resolution attempts should be
     * cached in the local repository. If caching is enabled, resolution will not be reattempted until the update policy
     * for the affected resource has expired.
     * 
     * @param transferErrorCachingEnabled {@code true} to cache transfer errors, {@code false} to always reattempt
     *            downloading.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setTransferErrorCachingEnabled( boolean transferErrorCachingEnabled )
    {
        failIfReadOnly();
        this.transferErrorCachingEnabled = transferErrorCachingEnabled;
        return this;
    }

    public boolean isNotFoundCachingEnabled()
    {
        return notFoundCachingEnabled;
    }

    /**
     * Controls whether missing artifacts/metadata from resolution attempts should be cached in the local repository. If
     * caching is enabled, resolution will not be reattempted until the update policy for the affected resource has
     * expired.
     * 
     * @param notFoundCachingEnabled {@code true} if to cache missing resources, {@code false} to always reattempt
     *            downloading.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setNotFoundCachingEnabled( boolean notFoundCachingEnabled )
    {
        failIfReadOnly();
        this.notFoundCachingEnabled = notFoundCachingEnabled;
        return this;
    }

    public boolean isIgnoreMissingArtifactDescriptor()
    {
        return ignoreMissingArtifactDescriptor;
    }

    /**
     * Controls whether missing artifact descriptors are silently ignored. If enabled and no artifact descriptor is
     * available, an empty stub descriptor is used instead.
     * 
     * @param ignoreMissingArtifactDescriptor {@code true} if to ignore missing artifact descriptors, {@code false} to
     *            fail the operation with an exception.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setIgnoreMissingArtifactDescriptor( boolean ignoreMissingArtifactDescriptor )
    {
        failIfReadOnly();
        this.ignoreMissingArtifactDescriptor = ignoreMissingArtifactDescriptor;
        return this;
    }

    public boolean isIgnoreInvalidArtifactDescriptor()
    {
        return ignoreInvalidArtifactDescriptor;
    }

    /**
     * Controls whether invalid artifact descriptors are silently ignored. If enabled and an artifact descriptor is
     * invalid, an empty stub descriptor is used instead.
     * 
     * @param ignoreInvalidArtifactDescriptor {@code true} if to ignore invalid artifact descriptors, {@code false} to
     *            fail the operation with an exception.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setIgnoreInvalidArtifactDescriptor( boolean ignoreInvalidArtifactDescriptor )
    {
        failIfReadOnly();
        this.ignoreInvalidArtifactDescriptor = ignoreInvalidArtifactDescriptor;
        return this;
    }

    public boolean isIgnoreArtifactDescriptorRepositories()
    {
        return ignoreArtifactDescriptorRepositories;
    }

    /**
     * Controls whether repositories declared in artifact descriptors should be ignored during transitive dependency
     * collection. If enabled, only the repositories originally provided with the collect request will be considered.
     * 
     * @param ignoreArtifactDescriptorRepositories {@code true} to ignore additional repositories from artifact
     *            descriptors, {@code false} to merge those with the originally specified repositories.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setIgnoreArtifactDescriptorRepositories( boolean ignoreArtifactDescriptorRepositories )
    {
        failIfReadOnly();
        this.ignoreArtifactDescriptorRepositories = ignoreArtifactDescriptorRepositories;
        return this;
    }

    public String getChecksumPolicy()
    {
        return checksumPolicy;
    }

    /**
     * Sets the global checksum policy. If set, the global checksum policy overrides the checksum policies of the remote
     * repositories being used for resolution.
     * 
     * @param checksumPolicy The global checksum policy, may be {@code null}/empty to apply the per-repository policies.
     * @return This session for chaining, never {@code null}.
     * @see RepositoryPolicy#CHECKSUM_POLICY_FAIL
     * @see RepositoryPolicy#CHECKSUM_POLICY_IGNORE
     * @see RepositoryPolicy#CHECKSUM_POLICY_WARN
     */
    public DefaultRepositorySystemSession setChecksumPolicy( String checksumPolicy )
    {
        failIfReadOnly();
        this.checksumPolicy = checksumPolicy;
        return this;
    }

    public String getUpdatePolicy()
    {
        return updatePolicy;
    }

    /**
     * Sets the global update policy. If set, the global update policy overrides the update policies of the remote
     * repositories being used for resolution.
     * 
     * @param updatePolicy The global update policy, may be {@code null}/empty to apply the per-repository policies.
     * @return This session for chaining, never {@code null}.
     * @see RepositoryPolicy#UPDATE_POLICY_ALWAYS
     * @see RepositoryPolicy#UPDATE_POLICY_DAILY
     * @see RepositoryPolicy#UPDATE_POLICY_NEVER
     */
    public DefaultRepositorySystemSession setUpdatePolicy( String updatePolicy )
    {
        failIfReadOnly();
        this.updatePolicy = updatePolicy;
        return this;
    }

    public LocalRepository getLocalRepository()
    {
        LocalRepositoryManager lrm = getLocalRepositoryManager();
        return ( lrm != null ) ? lrm.getRepository() : null;
    }

    public LocalRepositoryManager getLocalRepositoryManager()
    {
        return localRepositoryManager;
    }

    /**
     * Sets the local repository manager used during this session. <em>Note:</em> Eventually, a valid session must have
     * a local repository manager set.
     * 
     * @param localRepositoryManager The local repository manager used during this session, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setLocalRepositoryManager( LocalRepositoryManager localRepositoryManager )
    {
        failIfReadOnly();
        this.localRepositoryManager = localRepositoryManager;
        return this;
    }

    public WorkspaceReader getWorkspaceReader()
    {
        return workspaceReader;
    }

    /**
     * Sets the workspace reader used during this session. If set, the workspace reader will usually be consulted first
     * to resolve artifacts.
     * 
     * @param workspaceReader The workspace reader for this session, may be {@code null} if none.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setWorkspaceReader( WorkspaceReader workspaceReader )
    {
        failIfReadOnly();
        this.workspaceReader = workspaceReader;
        return this;
    }

    public RepositoryListener getRepositoryListener()
    {
        return repositoryListener;
    }

    /**
     * Sets the listener being notified of actions in the repository system.
     * 
     * @param repositoryListener The repository listener, may be {@code null} if none.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setRepositoryListener( RepositoryListener repositoryListener )
    {
        failIfReadOnly();
        this.repositoryListener = repositoryListener;
        return this;
    }

    public TransferListener getTransferListener()
    {
        return transferListener;
    }

    /**
     * Sets the listener being notified of uploads/downloads by the repository system.
     * 
     * @param transferListener The transfer listener, may be {@code null} if none.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setTransferListener( TransferListener transferListener )
    {
        failIfReadOnly();
        this.transferListener = transferListener;
        return this;
    }

    private <K, V> Map<K, V> copy( Map<K, V> map )
    {
        if ( map == null || map.isEmpty() )
        {
            map = new HashMap<K, V>();
        }
        else
        {
            map = new HashMap<K, V>( map );
        }
        return map;
    }

    private <T> Map<String, T> copySafe( Map<?, ?> table, Class<T> valueType )
    {
        Map<String, T> map;
        if ( table == null || table.isEmpty() )
        {
            map = new HashMap<String, T>();
        }
        else
        {
            map = new HashMap<String, T>();
            for ( Map.Entry<?, ?> entry : table.entrySet() )
            {
                Object key = entry.getKey();
                if ( key instanceof String )
                {
                    Object value = entry.getValue();
                    if ( valueType.isInstance( value ) )
                    {
                        map.put( key.toString(), valueType.cast( value ) );
                    }
                }
            }
        }
        return map;
    }

    public Map<String, String> getSystemProperties()
    {
        return systemPropertiesView;
    }

    /**
     * Sets the system properties to use, e.g. for processing of artifact descriptors. System properties are usually
     * collected from the runtime environment like {@link System#getProperties()} and environment variables.
     * 
     * @param systemProperties The system properties, may be {@code null} or empty if none.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setSystemProperties( Map<String, String> systemProperties )
    {
        failIfReadOnly();
        this.systemProperties = copy( systemProperties );
        systemPropertiesView = Collections.unmodifiableMap( this.systemProperties );
        return this;
    }

    /**
     * Sets the system properties to use, e.g. for processing of artifact descriptors. System properties are usually
     * collected from the runtime environment like {@link System#getProperties()} and environment variables.
     * 
     * @param systemProperties The system properties, may be {@code null} or empty if none.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setSystemProps( Map<?, ?> systemProperties )
    {
        failIfReadOnly();
        this.systemProperties = copySafe( systemProperties, String.class );
        systemPropertiesView = Collections.unmodifiableMap( this.systemProperties );
        return this;
    }

    /**
     * Sets the specified system property.
     * 
     * @param key The property key, must not be {@code null}.
     * @param value The property value, may be {@code null} to remove/unset the property.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setSystemProperty( String key, String value )
    {
        failIfReadOnly();
        if ( value != null )
        {
            systemProperties.put( key, value );
        }
        else
        {
            systemProperties.remove( key );
        }
        return this;
    }

    public Map<String, String> getUserProperties()
    {
        return userPropertiesView;
    }

    /**
     * Sets the user properties to use, e.g. for processing of artifact descriptors. User properties are similar to
     * system properties but are set on the discretion of the user and hence are considered of higher priority than
     * system properties.
     * 
     * @param userProperties The user properties, may be {@code null} or empty if none.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setUserProperties( Map<String, String> userProperties )
    {
        failIfReadOnly();
        this.userProperties = copy( userProperties );
        userPropertiesView = Collections.unmodifiableMap( this.userProperties );
        return this;
    }

    /**
     * Sets the user properties to use, e.g. for processing of artifact descriptors. User properties are similar to
     * system properties but are set on the discretion of the user and hence are considered of higher priority than
     * system properties.
     * 
     * @param userProperties The user properties, may be {@code null} or empty if none.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setUserProps( Map<?, ?> userProperties )
    {
        failIfReadOnly();
        this.userProperties = copySafe( userProperties, String.class );
        userPropertiesView = Collections.unmodifiableMap( this.userProperties );
        return this;
    }

    /**
     * Sets the specified user property.
     * 
     * @param key The property key, must not be {@code null}.
     * @param value The property value, may be {@code null} to remove/unset the property.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setUserProperty( String key, String value )
    {
        failIfReadOnly();
        if ( value != null )
        {
            userProperties.put( key, value );
        }
        else
        {
            userProperties.remove( key );
        }
        return this;
    }

    public Map<String, Object> getConfigProperties()
    {
        return configPropertiesView;
    }

    /**
     * Sets the configuration properties used to tweak internal aspects of the repository system (e.g. thread pooling,
     * connector-specific behavior, etc.)
     * 
     * @param configProperties The configuration properties, may be {@code null} or empty if none.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setConfigProperties( Map<String, Object> configProperties )
    {
        failIfReadOnly();
        this.configProperties = copy( configProperties );
        configPropertiesView = Collections.unmodifiableMap( this.configProperties );
        return this;
    }

    /**
     * Sets the configuration properties used to tweak internal aspects of the repository system (e.g. thread pooling,
     * connector-specific behavior, etc.)
     * 
     * @param configProperties The configuration properties, may be {@code null} or empty if none.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setConfigProps( Map<?, ?> configProperties )
    {
        failIfReadOnly();
        this.configProperties = copySafe( configProperties, Object.class );
        configPropertiesView = Collections.unmodifiableMap( this.configProperties );
        return this;
    }

    /**
     * Sets the specified configuration property.
     * 
     * @param key The property key, must not be {@code null}.
     * @param value The property value, may be {@code null} to remove/unset the property.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setConfigProperty( String key, Object value )
    {
        failIfReadOnly();
        if ( value != null )
        {
            configProperties.put( key, value );
        }
        else
        {
            configProperties.remove( key );
        }
        return this;
    }

    public MirrorSelector getMirrorSelector()
    {
        return mirrorSelector;
    }

    /**
     * Sets the mirror selector to use for repositories discovered in artifact descriptors. Note that this selector is
     * not used for remote repositories which are passed as request parameters to the repository system, those
     * repositories are supposed to denote the effective repositories.
     * 
     * @param mirrorSelector The mirror selector to use, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setMirrorSelector( MirrorSelector mirrorSelector )
    {
        failIfReadOnly();
        this.mirrorSelector = mirrorSelector;
        if ( this.mirrorSelector == null )
        {
            this.mirrorSelector = NullMirrorSelector.INSTANCE;
        }
        return this;
    }

    public ProxySelector getProxySelector()
    {
        return proxySelector;
    }

    /**
     * Sets the proxy selector to use for repositories discovered in artifact descriptors. Note that this selector is
     * not used for remote repositories which are passed as request parameters to the repository system, those
     * repositories are supposed to have their proxy (if any) already set.
     * 
     * @param proxySelector The proxy selector to use, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     * @see org.eclipse.aether.repository.RemoteRepository#getProxy()
     */
    public DefaultRepositorySystemSession setProxySelector( ProxySelector proxySelector )
    {
        failIfReadOnly();
        this.proxySelector = proxySelector;
        if ( this.proxySelector == null )
        {
            this.proxySelector = NullProxySelector.INSTANCE;
        }
        return this;
    }

    public AuthenticationSelector getAuthenticationSelector()
    {
        return authenticationSelector;
    }

    /**
     * Sets the authentication selector to use for repositories discovered in artifact descriptors. Note that this
     * selector is not used for remote repositories which are passed as request parameters to the repository system,
     * those repositories are supposed to have their authentication (if any) already set.
     * 
     * @param authenticationSelector The authentication selector to use, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     * @see org.eclipse.aether.repository.RemoteRepository#getAuthentication()
     */
    public DefaultRepositorySystemSession setAuthenticationSelector( AuthenticationSelector authenticationSelector )
    {
        failIfReadOnly();
        this.authenticationSelector = authenticationSelector;
        if ( this.authenticationSelector == null )
        {
            this.authenticationSelector = NullAuthenticationSelector.INSTANCE;
        }
        return this;
    }

    public ArtifactTypeRegistry getArtifactTypeRegistry()
    {
        return artifactTypeRegistry;
    }

    /**
     * Sets the registry of artifact types recognized by this session.
     * 
     * @param artifactTypeRegistry The artifact type registry, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setArtifactTypeRegistry( ArtifactTypeRegistry artifactTypeRegistry )
    {
        failIfReadOnly();
        this.artifactTypeRegistry = artifactTypeRegistry;
        if ( this.artifactTypeRegistry == null )
        {
            this.artifactTypeRegistry = NullArtifactTypeRegistry.INSTANCE;
        }
        return this;
    }

    public DependencyTraverser getDependencyTraverser()
    {
        return dependencyTraverser;
    }

    /**
     * Sets the dependency traverser to use for building dependency graphs.
     * 
     * @param dependencyTraverser The dependency traverser to use for building dependency graphs, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setDependencyTraverser( DependencyTraverser dependencyTraverser )
    {
        failIfReadOnly();
        this.dependencyTraverser = dependencyTraverser;
        if ( this.dependencyTraverser == null )
        {
            this.dependencyTraverser = NullDependencyTraverser.INSTANCE;
        }
        return this;
    }

    public DependencyManager getDependencyManager()
    {
        return dependencyManager;
    }

    /**
     * Sets the dependency manager to use for building dependency graphs.
     * 
     * @param dependencyManager The dependency manager to use for building dependency graphs, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setDependencyManager( DependencyManager dependencyManager )
    {
        failIfReadOnly();
        this.dependencyManager = dependencyManager;
        if ( this.dependencyManager == null )
        {
            this.dependencyManager = NullDependencyManager.INSTANCE;
        }
        return this;
    }

    public DependencySelector getDependencySelector()
    {
        return dependencySelector;
    }

    /**
     * Sets the dependency selector to use for building dependency graphs.
     * 
     * @param dependencySelector The dependency selector to use for building dependency graphs, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setDependencySelector( DependencySelector dependencySelector )
    {
        failIfReadOnly();
        this.dependencySelector = dependencySelector;
        if ( this.dependencySelector == null )
        {
            this.dependencySelector = NullDependencySelector.INSTANCE;
        }
        return this;
    }

    public DependencyGraphTransformer getDependencyGraphTransformer()
    {
        return dependencyGraphTransformer;
    }

    /**
     * Sets the dependency graph transformer to use for building dependency graphs.
     * 
     * @param dependencyGraphTransformer The dependency graph transformer to use for building dependency graphs, may be
     *            {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setDependencyGraphTransformer( DependencyGraphTransformer dependencyGraphTransformer )
    {
        failIfReadOnly();
        this.dependencyGraphTransformer = dependencyGraphTransformer;
        if ( this.dependencyGraphTransformer == null )
        {
            this.dependencyGraphTransformer = NullDependencyGraphTransformer.INSTANCE;
        }
        return this;
    }

    public SessionData getData()
    {
        return data;
    }

    /**
     * Sets the custom data associated with this session.
     * 
     * @param data The session data, may be {@code null}.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setData( SessionData data )
    {
        failIfReadOnly();
        this.data = data;
        if ( this.data == null )
        {
            this.data = new DefaultSessionData();
        }
        return this;
    }

    public RepositoryCache getCache()
    {
        return cache;
    }

    /**
     * Sets the cache the repository system may use to save data for future reuse during the session.
     * 
     * @param cache The repository cache, may be {@code null} if none.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setCache( RepositoryCache cache )
    {
        failIfReadOnly();
        this.cache = cache;
        return this;
    }

    /**
     * Marks this session as read-only such that any future attempts to call its mutators it will fail with an
     * exception. Marking an already read-only session as read-only has no effect. The session's data and cache remain
     * writable.
     */
    public void setReadOnly()
    {
        readOnly = true;
    }

    private void failIfReadOnly()
    {
        if ( readOnly )
        {
            throw new IllegalStateException( "repository system session is read-only" );
        }
    }

    static class NullProxySelector
        implements ProxySelector
    {

        public static final ProxySelector INSTANCE = new NullProxySelector();

        public Proxy getProxy( RemoteRepository repository )
        {
            return repository.getProxy();
        }

    }

    static class NullMirrorSelector
        implements MirrorSelector
    {

        public static final MirrorSelector INSTANCE = new NullMirrorSelector();

        public RemoteRepository getMirror( RemoteRepository repository )
        {
            return null;
        }

    }

    static class NullAuthenticationSelector
        implements AuthenticationSelector
    {

        public static final AuthenticationSelector INSTANCE = new NullAuthenticationSelector();

        public Authentication getAuthentication( RemoteRepository repository )
        {
            return repository.getAuthentication();
        }

    }

    static final class NullArtifactTypeRegistry
        implements ArtifactTypeRegistry
    {

        public static final ArtifactTypeRegistry INSTANCE = new NullArtifactTypeRegistry();

        public ArtifactType get( String typeId )
        {
            return null;
        }

    }

    static class NullDependencyGraphTransformer
        implements DependencyGraphTransformer
    {

        public static final DependencyGraphTransformer INSTANCE = new NullDependencyGraphTransformer();

        public DependencyNode transformGraph( DependencyNode node, DependencyGraphTransformationContext context )
            throws RepositoryException
        {
            return node;
        }

    }

    static class NullDependencyManager
        implements DependencyManager
    {

        public static final DependencyManager INSTANCE = new NullDependencyManager();

        public DependencyManager deriveChildManager( DependencyCollectionContext context )
        {
            return this;
        }

        public DependencyManagement manageDependency( Dependency dependency )
        {
            return null;
        }

    }

    static class NullDependencySelector
        implements DependencySelector
    {

        public static final DependencySelector INSTANCE = new NullDependencySelector();

        public DependencySelector deriveChildSelector( DependencyCollectionContext context )
        {
            return this;
        }

        public boolean selectDependency( Dependency dependency )
        {
            return true;
        }

    }

    static class NullDependencyTraverser
        implements DependencyTraverser
    {

        public static final DependencyTraverser INSTANCE = new NullDependencyTraverser();

        public DependencyTraverser deriveChildTraverser( DependencyCollectionContext context )
        {
            return this;
        }

        public boolean traverseDependency( Dependency dependency )
        {
            return true;
        }

    }

}
