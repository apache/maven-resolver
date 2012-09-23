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
package org.eclipse.aether.repository;

/**
 * A policy controlling access to a repository.
 */
public final class RepositoryPolicy
{

    /**
     * Never update locally cached data.
     */
    public static final String UPDATE_POLICY_NEVER = "never";

    /**
     * Always update locally cached data.
     */
    public static final String UPDATE_POLICY_ALWAYS = "always";

    /**
     * Update locally cached data once a day.
     */
    public static final String UPDATE_POLICY_DAILY = "daily";

    /**
     * Update locally cached data every X minutes as given by "interval:X".
     */
    public static final String UPDATE_POLICY_INTERVAL = "interval";

    /**
     * Verify checksums and fail the resolution if they do not match.
     */
    public static final String CHECKSUM_POLICY_FAIL = "fail";

    /**
     * Verify checksums and warn if they do not match.
     */
    public static final String CHECKSUM_POLICY_WARN = "warn";

    /**
     * Do not verify checksums.
     */
    public static final String CHECKSUM_POLICY_IGNORE = "ignore";

    private final boolean enabled;

    private final String updatePolicy;

    private final String checksumPolicy;

    /**
     * Creates a new policy with checksum warnings and daily update checks.
     */
    public RepositoryPolicy()
    {
        this( true, UPDATE_POLICY_DAILY, CHECKSUM_POLICY_WARN );
    }

    /**
     * Creates a new policy with the specified settings.
     * 
     * @param enabled A flag whether the associated repository should be accessed or not.
     * @param updatePolicy The update interval after which locally cached data from the repository is considered stale
     *            and should be refetched, may be {@code null}.
     * @param checksumPolicy The way checksum verification should be handled, may be {@code null}.
     */
    public RepositoryPolicy( boolean enabled, String updatePolicy, String checksumPolicy )
    {
        this.enabled = enabled;
        this.updatePolicy = ( updatePolicy != null ) ? updatePolicy : "";
        this.checksumPolicy = ( checksumPolicy != null ) ? checksumPolicy : "";
    }

    /**
     * Indicates whether the associated repository should be contacted or not.
     * 
     * @return {@code true} if the repository should be contacted, {@code false} otherwise.
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Gets the update policy for locally cached data from the repository.
     * 
     * @return The update policy, never {@code null}.
     */
    public String getUpdatePolicy()
    {
        return updatePolicy;
    }

    /**
     * Gets the policy for checksum validation.
     * 
     * @return The checksum policy, never {@code null}.
     */
    public String getChecksumPolicy()
    {
        return checksumPolicy;
    }

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder( 256 );
        buffer.append( "enabled=" ).append( isEnabled() );
        buffer.append( ", checksums=" ).append( getChecksumPolicy() );
        buffer.append( ", updates=" ).append( getUpdatePolicy() );
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

        RepositoryPolicy that = (RepositoryPolicy) obj;

        return enabled == that.enabled && updatePolicy.equals( that.updatePolicy )
            && checksumPolicy.equals( that.checksumPolicy );
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + ( enabled ? 1 : 0 );
        hash = hash * 31 + updatePolicy.hashCode();
        hash = hash * 31 + checksumPolicy.hashCode();
        return hash;
    }

}
