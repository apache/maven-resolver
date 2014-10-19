/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
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
     * The prefix for properties that control the priority of pluggable extensions like transporters. For example, for
     * an extension with the fully qualified class name "org.eclipse.MyExtensionFactory", the configuration properties
     * "aether.priority.org.eclipse.MyExtensionFactory", "aether.priority.MyExtensionFactory" and
     * "aether.priority.MyExtension" will be consulted for the priority, in that order (obviously, the last key is only
     * tried if the class name ends with "Factory"). The corresponding value is a float and the special value
     * {@link Float#NaN} or "NaN" (case-sensitive) can be used to disable the extension.
     */
    public static final String PREFIX_PRIORITY = PREFIX_AETHER + "priority.";

    /**
     * A flag indicating whether the priorities of pluggable extensions are implicitly given by their iteration order
     * such that the first extension has the highest priority. If set, an extension's built-in priority as well as any
     * corresponding {@code aether.priority.*} configuration properties are ignored when searching for a suitable
     * implementation among the available extensions. This priority mode is meant for cases where the application will
     * present/inject extensions in the desired search order.
     * 
     * @see #DEFAULT_IMPLICIT_PRIORITIES
     */
    public static final String IMPLICIT_PRIORITIES = PREFIX_PRIORITY + "implicit";

    /**
     * The default extension priority mode if {@link #IMPLICIT_PRIORITIES} isn't set.
     */
    public static final boolean DEFAULT_IMPLICIT_PRIORITIES = false;

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
     * An option indicating whether authentication configured for a HTTP repository should also be used with any host
     * that the original server might redirect requests to. Unless enabled, credentials are only exchanged with the
     * original host from the repository URL and not supplied to different hosts encountered during redirects. The
     * option value can either be a boolean flag or a comma-separated list of host names denoting the whitelist of
     * original hosts whose redirects can be trusted and should use the configured authentication no matter the
     * destination host(s). Alternatively, the suffix {@code .<repoId>} can be appended to this configuration key to
     * control the behavior for a specific repository id.
     * 
     * @see #DEFAULT_HTTP_REDIRECTED_AUTHENTICATION
     * @since 1.1.0
     */
    public static final String HTTP_REDIRECTED_AUTHENTICATION = PREFIX_CONNECTOR + "http.redirectedAuthentication";

    /**
     * The default handling of authentication during HTTP redirects if {@link #HTTP_REDIRECTED_AUTHENTICATION} isn't
     * set.
     * 
     * @since 1.1.0
     */
    public static final String DEFAULT_HTTP_REDIRECTED_AUTHENTICATION = "false";

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
