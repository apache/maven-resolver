/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether;

/**
 * The keys and defaults for common configuration properties.
 * 
 * @see RepositorySystemSession#getConfigProperties()
 */
public final class ConfigurationProperties
{

    private static final String PREFIX_AETHER = "aether.";

    private static final String PREFIX_CONNECTOR = PREFIX_AETHER + "connector.";

    /**
     * A flag indicating whether interaction with the user is allowed.
     * 
     * @see #DEFAULT_INTERACTIVE
     */
    public static final String INTERACTIVE = PREFIX_AETHER + "interactive";

    /**
     * The default interactive mode if {@link #INTERACTIVE} isn't set.
     */
    public static final boolean DEFAULT_INTERACTIVE = false;

    /**
     * The user agent that repository connectors should report to servers.
     * 
     * @see #DEFAULT_USER_AGENT
     */
    public static final String USER_AGENT = PREFIX_CONNECTOR + "userAgent";

    /**
     * The default user agent to use if {@link #USER_AGENT} isn't set.
     */
    public static final String DEFAULT_USER_AGENT = "Aether";

    /**
     * The maximum amount of time (in milliseconds) to wait for a successful connection to a remote server. Non-positive
     * values indicate no timeout.
     * 
     * @see #DEFAULT_CONNECT_TIMEOUT
     */
    public static final String CONNECT_TIMEOUT = PREFIX_CONNECTOR + "connectTimeout";

    /**
     * The default connect timeout to use if {@link #CONNECT_TIMEOUT} isn't set.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT = 10 * 1000;

    /**
     * The maximum amount of time (in milliseconds) to wait for remaining data to arrive from a remote server. Note that
     * this timeout does not restrict the overall duration of a request, it only restricts the duration of inactivity
     * between consecutive data packets. Non-positive values indicate no timeout.
     * 
     * @see #DEFAULT_REQUEST_TIMEOUT
     */
    public static final String REQUEST_TIMEOUT = PREFIX_CONNECTOR + "requestTimeout";

    /**
     * The default request timeout to use if {@link #REQUEST_TIMEOUT} isn't set.
     */
    public static final int DEFAULT_REQUEST_TIMEOUT = 1800 * 1000;

    /**
     * The request headers to use for HTTP-based repository connectors. The headers are specified using a
     * {@code Map<String, String>}, mapping a header name to its value. Besides this general key, clients may also
     * specify headers for a specific remote repository by appending the suffix {@code .<repoId>} to this key when
     * storing the headers map. The repository-specific headers map is supposed to be complete, i.e. is not merged with
     * the general headers map.
     */
    public static final String HTTP_HEADERS = PREFIX_CONNECTOR + "http.headers";

    /**
     * The encoding/charset to use when exchanging credentials with HTTP servers. Besides this general key, clients may
     * also specify the encoding for a specific remote repository by appending the suffix {@code .<repoId>} to this key
     * when storing the charset name.
     * 
     * @see #DEFAULT_HTTP_CREDENTIAL_ENCODING
     */
    public static final String HTTP_CREDENTIAL_ENCODING = PREFIX_CONNECTOR + "http.credentialEncoding";

    /**
     * The default encoding/charset to use if {@link #HTTP_CREDENTIAL_ENCODING} isn't set.
     */
    public static final String DEFAULT_HTTP_CREDENTIAL_ENCODING = "ISO-8859-1";

    /**
     * A flag indicating whether checksums which are retrieved during checksum validation should be persisted in the
     * local filesystem next to the file they provide the checksum for.
     * 
     * @see #DEFAULT_PERSISTED_CHECKSUMS
     */
    public static final String PERSISTED_CHECKSUMS = PREFIX_CONNECTOR + "persistedChecksums";

    /**
     * The default checksum persistence mode if {@link #PERSISTED_CHECKSUMS} isn't set.
     */
    public static final boolean DEFAULT_PERSISTED_CHECKSUMS = true;

    private ConfigurationProperties()
    {
        // hide constructor
    }

}
