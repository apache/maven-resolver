/*******************************************************************************
 * Copyright (c) 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.repository;

import java.util.ArrayList;
import java.util.List;

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
    public AuthenticationBuilder username( String username )
    {
        if ( username != null )
        {
            authentications.add( new StringAuthentication( AuthenticationContext.USERNAME, username ) );
        }
        return this;
    }

    /**
     * Adds password data to the authentication.
     * 
     * @param password The password, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder password( String password )
    {
        if ( password != null )
        {
            authentications.add( new SecretAuthentication( AuthenticationContext.PASSWORD, password ) );
        }
        return this;
    }

    /**
     * Adds password data to the authentication.
     * 
     * @param password The password, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder password( char[] password )
    {
        if ( password != null )
        {
            authentications.add( new SecretAuthentication( AuthenticationContext.PASSWORD, password ) );
        }
        return this;
    }

    /**
     * Adds NTLM data to the authentication.
     * 
     * @param workstation The NTLM workstation name, may be {@code null}.
     * @param domain The NTLM domain name, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder ntlm( String workstation, String domain )
    {
        if ( workstation != null )
        {
            authentications.add( new StringAuthentication( AuthenticationContext.NTLM_WORKSTATION, workstation ) );
        }
        if ( domain != null )
        {
            authentications.add( new StringAuthentication( AuthenticationContext.NTLM_DOMAIN, domain ) );
        }
        return this;
    }

    /**
     * Adds private key data to the authentication.
     * 
     * @param pathname The (absolute) path to the private key file, may be {@code null}.
     * @param passphrase The passphrase protecting the private key, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder privateKey( String pathname, String passphrase )
    {
        if ( pathname != null )
        {
            authentications.add( new StringAuthentication( AuthenticationContext.PRIVATE_KEY_PATH, pathname ) );
            if ( passphrase != null )
            {
                authentications.add( new SecretAuthentication( AuthenticationContext.PRIVATE_KEY_PASSPHRASE, passphrase ) );
            }
        }
        return this;
    }

    /**
     * Adds private key data to the authentication.
     * 
     * @param pathname The (absolute) path to the private key file, may be {@code null}.
     * @param passphrase The passphrase protecting the private key, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder privateKey( String pathname, char[] passphrase )
    {
        if ( pathname != null )
        {
            authentications.add( new StringAuthentication( AuthenticationContext.PRIVATE_KEY_PATH, pathname ) );
            if ( passphrase != null )
            {
                authentications.add( new SecretAuthentication( AuthenticationContext.PRIVATE_KEY_PASSPHRASE, passphrase ) );
            }
        }
        return this;
    }

    /**
     * Adds custom string data to the authentication. <em>Note:</em> If the string data is confidential, use
     * {@link #secret(String, char[])} instead.
     * 
     * @param key The key for the authentication data, must not be {@code null}.
     * @param value The value for the authentication data, may be {@code null}.
     * @return This builder for chaining, never {@code null}.
     */
    public AuthenticationBuilder string( String key, String value )
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
    public AuthenticationBuilder secret( String key, String value )
    {
        if ( value != null )
        {
            authentications.add( new SecretAuthentication( key, value ) );
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
    public AuthenticationBuilder secret( String key, char[] value )
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
    public AuthenticationBuilder custom( Authentication authentication )
    {
        if ( authentication != null )
        {
            authentications.add( authentication );
        }
        return this;
    }

}
