package org.eclipse.aether.repository;

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

import java.io.Closeable;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import static java.util.Objects.requireNonNull;

import org.eclipse.aether.RepositorySystemSession;

/**
 * A glorified map of key value pairs holding (cleartext) authentication data. Authentication contexts are used
 * internally when network operations need to access secured repositories or proxies. Each authentication context
 * manages the credentials required to access a single host. Unlike {@link Authentication} callbacks which exist for a
 * potentially long time like the duration of a repository system session, an authentication context has a supposedly
 * short lifetime and should be {@link #close() closed} as soon as the corresponding network operation has finished:
 * 
 * <pre>
 * AuthenticationContext context = AuthenticationContext.forRepository( session, repository );
 * try {
 *     // get credentials
 *     char[] password = context.get( AuthenticationContext.PASSWORD, char[].class );
 *     // perform network operation using retrieved credentials
 *     ...
 * } finally {
 *     // erase confidential authentication data from heap memory
 *     AuthenticationContext.close( context );
 * }
 * </pre>
 * 
 * The same authentication data can often be presented using different data types, e.g. a password can be presented
 * using a character array or (less securely) using a string. For ease of use, an authentication context treats the
 * following groups of data types as equivalent and converts values automatically during retrieval:
 * <ul>
 * <li>{@code String}, {@code char[]}</li>
 * <li>{@code String}, {@code File}</li>
 * </ul>
 * An authentication context is thread-safe.
 */
public final class AuthenticationContext
    implements Closeable
{

    /**
     * The key used to store the username. The corresponding authentication data should be of type {@link String}.
     */
    public static final String USERNAME = "username";

    /**
     * The key used to store the password. The corresponding authentication data should be of type {@code char[]} or
     * {@link String}.
     */
    public static final String PASSWORD = "password";

    /**
     * The key used to store the NTLM domain. The corresponding authentication data should be of type {@link String}.
     */
    public static final String NTLM_DOMAIN = "ntlm.domain";

    /**
     * The key used to store the NTML workstation. The corresponding authentication data should be of type
     * {@link String}.
     */
    public static final String NTLM_WORKSTATION = "ntlm.workstation";

    /**
     * The key used to store the pathname to a private key file. The corresponding authentication data should be of type
     * {@link String} or {@link File}.
     */
    public static final String PRIVATE_KEY_PATH = "privateKey.path";

    /**
     * The key used to store the passphrase protecting the private key. The corresponding authentication data should be
     * of type {@code char[]} or {@link String}.
     */
    public static final String PRIVATE_KEY_PASSPHRASE = "privateKey.passphrase";

    /**
     * The key used to store the acceptance policy for unknown host keys. The corresponding authentication data should
     * be of type {@link Boolean}. When querying this authentication data, the extra data should provide
     * {@link #HOST_KEY_REMOTE} and {@link #HOST_KEY_LOCAL}, e.g. to enable a well-founded decision of the user during
     * an interactive prompt.
     */
    public static final String HOST_KEY_ACCEPTANCE = "hostKey.acceptance";

    /**
     * The key used to store the fingerprint of the public key advertised by remote host. Note that this key is used to
     * query the extra data passed to {@link #get(String, Map, Class)} when getting {@link #HOST_KEY_ACCEPTANCE}, not
     * the authentication data in a context.
     */
    public static final String HOST_KEY_REMOTE = "hostKey.remote";

    /**
     * The key used to store the fingerprint of the public key expected from remote host as recorded in a known hosts
     * database. Note that this key is used to query the extra data passed to {@link #get(String, Map, Class)} when
     * getting {@link #HOST_KEY_ACCEPTANCE}, not the authentication data in a context.
     */
    public static final String HOST_KEY_LOCAL = "hostKey.local";

    /**
     * The key used to store the SSL context. The corresponding authentication data should be of type
     * {@link javax.net.ssl.SSLContext}.
     */
    public static final String SSL_CONTEXT = "ssl.context";

    /**
     * The key used to store the SSL hostname verifier. The corresponding authentication data should be of type
     * {@link javax.net.ssl.HostnameVerifier}.
     */
    public static final String SSL_HOSTNAME_VERIFIER = "ssl.hostnameVerifier";

    private final RepositorySystemSession session;

    private final RemoteRepository repository;

    private final Proxy proxy;

    private final Authentication auth;

    private final Map<String, Object> authData;

    private boolean fillingAuthData;

    /**
     * Gets an authentication context for the specified repository.
     * 
     * @param session The repository system session during which the repository is accessed, must not be {@code null}.
     * @param repository The repository for which to create an authentication context, must not be {@code null}.
     * @return An authentication context for the repository or {@code null} if no authentication is configured for it.
     */
    public static AuthenticationContext forRepository( RepositorySystemSession session, RemoteRepository repository )
    {
        return newInstance( session, repository, null, repository.getAuthentication() );
    }

    /**
     * Gets an authentication context for the proxy of the specified repository.
     * 
     * @param session The repository system session during which the repository is accessed, must not be {@code null}.
     * @param repository The repository for whose proxy to create an authentication context, must not be {@code null}.
     * @return An authentication context for the proxy or {@code null} if no proxy is set or no authentication is
     *         configured for it.
     */
    public static AuthenticationContext forProxy( RepositorySystemSession session, RemoteRepository repository )
    {
        Proxy proxy = repository.getProxy();
        return newInstance( session, repository, proxy, ( proxy != null ) ? proxy.getAuthentication() : null );
    }

    private static AuthenticationContext newInstance( RepositorySystemSession session, RemoteRepository repository,
                                                      Proxy proxy, Authentication auth )
    {
        if ( auth == null )
        {
            return null;
        }
        return new AuthenticationContext( session, repository, proxy, auth );
    }

    private AuthenticationContext( RepositorySystemSession session, RemoteRepository repository, Proxy proxy,
                                   Authentication auth )
    {
        this.session = requireNonNull( session, "repository system session cannot be null" );
        this.repository = repository;
        this.proxy = proxy;
        this.auth = auth;
        authData = new HashMap<>();
    }

    /**
     * Gets the repository system session during which the authentication happens.
     * 
     * @return The repository system session, never {@code null}.
     */
    public RepositorySystemSession getSession()
    {
        return session;
    }

    /**
     * Gets the repository requiring authentication. If {@link #getProxy()} is not {@code null}, the data gathered by
     * this authentication context does not apply to the repository's host but rather the proxy.
     * 
     * @return The repository to be contacted, never {@code null}.
     */
    public RemoteRepository getRepository()
    {
        return repository;
    }

    /**
     * Gets the proxy (if any) to be authenticated with.
     * 
     * @return The proxy or {@code null} if authenticating directly with the repository's host.
     */
    public Proxy getProxy()
    {
        return proxy;
    }

    /**
     * Gets the authentication data for the specified key.
     * 
     * @param key The key whose authentication data should be retrieved, must not be {@code null}.
     * @return The requested authentication data or {@code null} if none.
     */
    public String get( String key )
    {
        return get( key, null, String.class );
    }

    /**
     * Gets the authentication data for the specified key.
     * 
     * @param <T> The data type of the authentication data.
     * @param key The key whose authentication data should be retrieved, must not be {@code null}.
     * @param type The expected type of the authentication data, must not be {@code null}.
     * @return The requested authentication data or {@code null} if none or if the data doesn't match the expected type.
     */
    public <T> T get( String key, Class<T> type )
    {
        return get( key, null, type );
    }

    /**
     * Gets the authentication data for the specified key.
     * 
     * @param <T> The data type of the authentication data.
     * @param key The key whose authentication data should be retrieved, must not be {@code null}.
     * @param data Any (read-only) extra data in form of key value pairs that might be useful when getting the
     *            authentication data, may be {@code null}.
     * @param type The expected type of the authentication data, must not be {@code null}.
     * @return The requested authentication data or {@code null} if none or if the data doesn't match the expected type.
     */
    public <T> T get( String key, Map<String, String> data, Class<T> type )
    {
        requireNonNull( key, "authentication key cannot be null" );
        if ( key.length() == 0 )
        {
            throw new IllegalArgumentException( "authentication key cannot be empty" );
        }

        Object value;
        synchronized ( authData )
        {
            value = authData.get( key );
            if ( value == null && !authData.containsKey( key ) && !fillingAuthData )
            {
                if ( auth != null )
                {
                    try
                    {
                        fillingAuthData = true;
                        auth.fill( this, key, data );
                    }
                    finally
                    {
                        fillingAuthData = false;
                    }
                    value = authData.get( key );
                }
                if ( value == null )
                {
                    authData.put( key, value );
                }
            }
        }

        return convert( value, type );
    }

    private <T> T convert( Object value, Class<T> type )
    {
        if ( !type.isInstance( value ) )
        {
            if ( String.class.equals( type ) )
            {
                if ( value instanceof File )
                {
                    value = ( (File) value ).getPath();
                }
                else if ( value instanceof char[] )
                {
                    value = new String( (char[]) value );
                }
            }
            else if ( File.class.equals( type ) )
            {
                if ( value instanceof String )
                {
                    value = new File( (String) value );
                }
            }
            else if ( char[].class.equals( type ) )
            {
                if ( value instanceof String )
                {
                    value = ( (String) value ).toCharArray();
                }
            }
        }

        if ( type.isInstance( value ) )
        {
            return type.cast( value );
        }

        return null;
    }

    /**
     * Puts the specified authentication data into this context. This method should only be called from implementors of
     * {@link Authentication#fill(AuthenticationContext, String, Map)}. Passed in character arrays are not cloned and
     * become owned by this context, i.e. get erased when the context gets closed.
     *
     * @param key The key to associate the authentication data with, must not be {@code null}.
     * @param value The (cleartext) authentication data to store, may be {@code null}.
     */
    public void put( String key, Object value )
    {
        requireNonNull( key, "authentication key cannot be null" );
        if ( key.length() == 0 )
        {
            throw new IllegalArgumentException( "authentication key cannot be empty" );
        }

        synchronized ( authData )
        {
            Object oldValue = authData.put( key, value );
            if ( oldValue instanceof char[] )
            {
                Arrays.fill( (char[]) oldValue, '\0' );
            }
        }
    }

    /**
     * Closes this authentication context and erases sensitive authentication data from heap memory. Closing an already
     * closed context has no effect.
     */
    public void close()
    {
        synchronized ( authData )
        {
            for ( Object value : authData.values() )
            {
                if ( value instanceof char[] )
                {
                    Arrays.fill( (char[]) value, '\0' );
                }
            }
            authData.clear();
        }
    }

    /**
     * Closes the specified authentication context. This is a convenience method doing a {@code null} check before
     * calling {@link #close()} on the given context.
     * 
     * @param context The authentication context to close, may be {@code null}.
     */
    public static void close( AuthenticationContext context )
    {
        if ( context != null )
        {
            context.close();
        }
    }

}
