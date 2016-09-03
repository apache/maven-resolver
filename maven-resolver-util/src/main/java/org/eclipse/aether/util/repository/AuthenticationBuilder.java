package org.eclipse.aether.util.repository;

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
import java.util.List;

import javax.net.ssl.HostnameVerifier;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;

/**
 * A utility class to build authentication info for repositories and proxies.
 */
public final class AuthenticationBuilder
{

    private final List<Authentication> authentications;

    /**
     * Creates a new authentication builder.
     */
    public AuthenticationBuilder()
    {
        authentications = new ArrayList<Authentication>();
    }

    /**
     * Builds a new authentication object from the current data of this builder. The state of the builder itself remains
     * unchanged.
     * 
     * @return The authentication or {@code null} if no authentication data was supplied to the builder.
     */
    public Authentication build()
    {
        if ( authentications.isEmpty() )
        {
            return null;
        }
        if ( authentications.size() == 1 )
        {
            return authentications.get( 0 );
        }
        return new ChainedAuthentication( authentications );
    }

    /**
     * Adds username data to the authentication.
     * 
     * @param username The username, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder addUsername( String username )
    {
        return addString( AuthenticationContext.USERNAME, username );
    }

    /**
     * Adds password data to the authentication.
     * 
     * @param password The password, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder addPassword( String password )
    {
        return addSecret( AuthenticationContext.PASSWORD, password );
    }

    /**
     * Adds password data to the authentication. The resulting authentication object uses an encrypted copy of the
     * supplied character data and callers are advised to clear the input array soon after this method returns.
     * 
     * @param password The password, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder addPassword( char[] password )
    {
        return addSecret( AuthenticationContext.PASSWORD, password );
    }

    /**
     * Adds NTLM data to the authentication.
     * 
     * @param workstation The NTLM workstation name, may be {@code null}.
     * @param domain The NTLM domain name, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder addNtlm( String workstation, String domain )
    {
        addString( AuthenticationContext.NTLM_WORKSTATION, workstation );
        return addString( AuthenticationContext.NTLM_DOMAIN, domain );
    }

    /**
     * Adds private key data to the authentication.
     * 
     * @param pathname The (absolute) path to the private key file, may be {@code null}.
     * @param passphrase The passphrase protecting the private key, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder addPrivateKey( String pathname, String passphrase )
    {
        if ( pathname != null )
        {
            addString( AuthenticationContext.PRIVATE_KEY_PATH, pathname );
            addSecret( AuthenticationContext.PRIVATE_KEY_PASSPHRASE, passphrase );
        }
        return this;
    }

    /**
     * Adds private key data to the authentication. The resulting authentication object uses an encrypted copy of the
     * supplied character data and callers are advised to clear the input array soon after this method returns.
     * 
     * @param pathname The (absolute) path to the private key file, may be {@code null}.
     * @param passphrase The passphrase protecting the private key, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder addPrivateKey( String pathname, char[] passphrase )
    {
        if ( pathname != null )
        {
            addString( AuthenticationContext.PRIVATE_KEY_PATH, pathname );
            addSecret( AuthenticationContext.PRIVATE_KEY_PASSPHRASE, passphrase );
        }
        return this;
    }

    /**
     * Adds a hostname verifier for SSL. <strong>Note:</strong> This method assumes that all possible instances of the
     * verifier's runtime type exhibit the exact same behavior, i.e. the behavior of the verifier depends solely on the
     * runtime type and not on any configuration. For verifiers that do not fit this assumption, use
     * {@link #addCustom(Authentication)} with a suitable implementation instead.
     * 
     * @param verifier The hostname verifier, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder addHostnameVerifier( HostnameVerifier verifier )
    {
        if ( verifier != null )
        {
            authentications.add( new ComponentAuthentication( AuthenticationContext.SSL_HOSTNAME_VERIFIER, verifier ) );
        }
        return this;
    }

    /**
     * Adds custom string data to the authentication. <em>Note:</em> If the string data is confidential, use
     * {@link #addSecret(String, char[])} instead.
     * 
     * @param key The key for the authentication data, must not be {@code null}.
     * @param value The value for the authentication data, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder addString( String key, String value )
    {
        if ( value != null )
        {
            authentications.add( new StringAuthentication( key, value ) );
        }
        return this;
    }

    /**
     * Adds sensitive custom string data to the authentication.
     * 
     * @param key The key for the authentication data, must not be {@code null}.
     * @param value The value for the authentication data, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder addSecret( String key, String value )
    {
        if ( value != null )
        {
            authentications.add( new SecretAuthentication( key, value ) );
        }
        return this;
    }

    /**
     * Adds sensitive custom string data to the authentication. The resulting authentication object uses an encrypted
     * copy of the supplied character data and callers are advised to clear the input array soon after this method
     * returns.
     * 
     * @param key The key for the authentication data, must not be {@code null}.
     * @param value The value for the authentication data, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder addSecret( String key, char[] value )
    {
        if ( value != null )
        {
            authentications.add( new SecretAuthentication( key, value ) );
        }
        return this;
    }

    /**
     * Adds custom authentication data to the authentication.
     * 
     * @param authentication The authentication to add, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder addCustom( Authentication authentication )
    {
        if ( authentication != null )
        {
            authentications.add( authentication );
        }
        return this;
    }

}
