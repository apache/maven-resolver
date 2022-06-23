package org.apache.maven.resolver;

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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static java.util.Objects.requireNonNull;

import java.util.Collection;

import org.apache.maven.resolver.artifact.Artifact;
import org.apache.maven.resolver.artifact.ArtifactType;
import org.apache.maven.resolver.artifact.ArtifactTypeRegistry;
import org.apache.maven.resolver.collection.DependencyGraphTransformer;
import org.apache.maven.resolver.collection.DependencyManager;
import org.apache.maven.resolver.collection.DependencySelector;
import org.apache.maven.resolver.collection.DependencyTraverser;
import org.apache.maven.resolver.collection.VersionFilter;
import org.apache.maven.resolver.repository.Authentication;
import org.apache.maven.resolver.repository.AuthenticationSelector;
import org.apache.maven.resolver.repository.LocalRepository;
import org.apache.maven.resolver.repository.LocalRepositoryManager;
import org.apache.maven.resolver.repository.MirrorSelector;
import org.apache.maven.resolver.repository.Proxy;
import org.apache.maven.resolver.repository.ProxySelector;
import org.apache.maven.resolver.repository.RemoteRepository;
import org.apache.maven.resolver.repository.RepositoryPolicy;
import org.apache.maven.resolver.repository.WorkspaceReader;
import org.apache.maven.resolver.resolution.ArtifactDescriptorPolicy;
import org.apache.maven.resolver.resolution.ResolutionErrorPolicy;
import org.apache.maven.resolver.transfer.TransferListener;
import org.apache.maven.resolver.transform.FileTransformer;
import org.apache.maven.resolver.transform.FileTransformerManager;

/**
 * A simple repository system session.
 * <p>
 * <strong>Note:</strong> This class is not thread-safe. It is assumed that the mutators get only called during an
 * initialization phase and that the session itself is not changed once initialized and being used by the repository
 * system. It is recommended to call {@link #setReadOnly()} once the session has been fully initialized to prevent
 * accidental manipulation of it afterwards.
 */
public final class DefaultRepositorySystemSession
    implements RepositorySystemSession
{

    private boolean readOnly;

    private boolean offline;

    private boolean ignoreArtifactDescriptorRepositories;

    private ResolutionErrorPolicy resolutionErrorPolicy;

    private ArtifactDescriptorPolicy artifactDescriptorPolicy;

    private String checksumPolicy;

    private String updatePolicy;

    private LocalRepositoryManager localRepositoryManager;

    private FileTransformerManager fileTransformerManager;

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

    private VersionFilter versionFilter;

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
        systemProperties = new HashMap<>();
        systemPropertiesView = Collections.unmodifiableMap( systemProperties );
        userProperties = new HashMap<>();
        userPropertiesView = Collections.unmodifiableMap( userProperties );
        configProperties = new HashMap<>();
        configPropertiesView = Collections.unmodifiableMap( configProperties );
        mirrorSelector = NullMirrorSelector.INSTANCE;
        proxySelector = NullProxySelector.INSTANCE;
        authenticationSelector = NullAuthenticationSelector.INSTANCE;
        artifactTypeRegistry = NullArtifactTypeRegistry.INSTANCE;
        fileTransformerManager = NullFileTransformerManager.INSTANCE;
        data = new DefaultSessionData();
    }

    /**
     * Creates a shallow copy of the specified session. Actually, the copy is not completely shallow, all maps holding
     * system/user/config properties are copied as well. In other words, invoking any mutator on the new session itself
     * has no effect on the original session. Other mutable objects like the session data and cache (if any) are not
     * copied and will be shared with the original session unless reconfigured.
     *
     * @param session The session to copy, must not be {@code null}.
     */
    public DefaultRepositorySystemSession( RepositorySystemSession session )
    {
        requireNonNull( session, "repository system session cannot be null" );

        setOffline( session.isOffline() );
        setIgnoreArtifactDescriptorRepositories( session.isIgnoreArtifactDescriptorRepositories() );
        setResolutionErrorPolicy( session.getResolutionErrorPolicy() );
        setArtifactDescriptorPolicy( session.getArtifactDescriptorPolicy() );
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
        setVersionFilter( session.getVersionFilter() );
        setDependencyGraphTransformer( session.getDependencyGraphTransformer() );
        setFileTransformerManager( session.getFileTransformerManager() );
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
    public DefaultRepositorySystemSession setIgnoreArtifactDescriptorRepositories(
            boolean ignoreArtifactDescriptorRepositories )
    {
        failIfReadOnly();
        this.ignoreArtifactDescriptorRepositories = ignoreArtifactDescriptorRepositories;
        return this;
    }

    public ResolutionErrorPolicy getResolutionErrorPolicy()
    {
        return resolutionErrorPolicy;
    }

    /**
     * Sets the policy which controls whether resolutions errors from remote repositories should be cached.
     * 
     * @param resolutionErrorPolicy The resolution error policy for this session, may be {@code null} if resolution
     *            errors should generally not be cached.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setResolutionErrorPolicy( ResolutionErrorPolicy resolutionErrorPolicy )
    {
        failIfReadOnly();
        this.resolutionErrorPolicy = resolutionErrorPolicy;
        return this;
    }

    public ArtifactDescriptorPolicy getArtifactDescriptorPolicy()
    {
        return artifactDescriptorPolicy;
    }

    /**
     * Sets the policy which controls how errors related to reading artifact descriptors should be handled.
     * 
     * @param artifactDescriptorPolicy The descriptor error policy for this session, may be {@code null} if descriptor
     *            errors should generally not be tolerated.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setArtifactDescriptorPolicy(
            ArtifactDescriptorPolicy artifactDescriptorPolicy )
    {
        failIfReadOnly();
        this.artifactDescriptorPolicy = artifactDescriptorPolicy;
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

    @Override
    public FileTransformerManager getFileTransformerManager()
    {
        return fileTransformerManager;
    }

    public DefaultRepositorySystemSession setFileTransformerManager( FileTransformerManager fileTransformerManager )
    {
        failIfReadOnly();
        this.fileTransformerManager = fileTransformerManager;
        if ( this.fileTransformerManager == null )
        {
            this.fileTransformerManager = NullFileTransformerManager.INSTANCE;
        }
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

    @SuppressWarnings( "checkstyle:magicnumber" )
    private <T> Map<String, T> copySafe( Map<?, ?> table, Class<T> valueType )
    {
        Map<String, T> map;
        if ( table == null || table.isEmpty() )
        {
            map = new HashMap<>();
        }
        else
        {
            map = new HashMap<>( (int) ( table.size() / 0.75f ) + 1 );
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
     * <p>
     * <em>Note:</em> System properties are of type {@code Map<String, String>} and any key-value pair in the input map
     * that doesn't match this type will be silently ignored.
     * 
     * @param systemProperties The system properties, may be {@code null} or empty if none.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setSystemProperties( Map<?, ?> systemProperties )
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
     * system properties in case of conflicts.
     * <p>
     * <em>Note:</em> User properties are of type {@code Map<String, String>} and any key-value pair in the input map
     * that doesn't match this type will be silently ignored.
     * 
     * @param userProperties The user properties, may be {@code null} or empty if none.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setUserProperties( Map<?, ?> userProperties )
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
     * connector-specific behavior, etc.).
     * <p>
     * <em>Note:</em> Configuration properties are of type {@code Map<String, Object>} and any key-value pair in the
     * input map that doesn't match this type will be silently ignored.
     * 
     * @param configProperties The configuration properties, may be {@code null} or empty if none.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setConfigProperties( Map<?, ?> configProperties )
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
     * @see RemoteRepository#getProxy()
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
     * @see RemoteRepository#getAuthentication()
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
        return this;
    }

    public VersionFilter getVersionFilter()
    {
        return versionFilter;
    }

    /**
     * Sets the version filter to use for building dependency graphs.
     * 
     * @param versionFilter The version filter to use for building dependency graphs, may be {@code null} to not filter
     *            versions.
     * @return This session for chaining, never {@code null}.
     */
    public DefaultRepositorySystemSession setVersionFilter( VersionFilter versionFilter )
    {
        failIfReadOnly();
        this.versionFilter = versionFilter;
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
    public DefaultRepositorySystemSession setDependencyGraphTransformer(
            DependencyGraphTransformer dependencyGraphTransformer )
    {
        failIfReadOnly();
        this.dependencyGraphTransformer = dependencyGraphTransformer;
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
     * Marks this session as read-only such that any future attempts to call its mutators will fail with an exception.
     * Marking an already read-only session as read-only has no effect. The session's data and cache remain writable
     * though.
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
            requireNonNull( repository, "repository cannot be null" );
            return repository.getProxy();
        }

    }

    static class NullMirrorSelector
        implements MirrorSelector
    {

        public static final MirrorSelector INSTANCE = new NullMirrorSelector();

        public RemoteRepository getMirror( RemoteRepository repository )
        {
            requireNonNull( repository, "repository cannot be null" );
            return null;
        }

    }

    static class NullAuthenticationSelector
        implements AuthenticationSelector
    {

        public static final AuthenticationSelector INSTANCE = new NullAuthenticationSelector();

        public Authentication getAuthentication( RemoteRepository repository )
        {
            requireNonNull( repository, "repository cannot be null" );
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

    static final class NullFileTransformerManager implements FileTransformerManager
    {
        public static final FileTransformerManager INSTANCE = new NullFileTransformerManager();

        @Override
        public Collection<FileTransformer> getTransformersForArtifact( Artifact artifact )
        {
            return Collections.emptyList();
        }
    }

}
