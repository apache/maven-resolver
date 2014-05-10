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
package org.eclipse.aether.impl;

import org.eclipse.aether.RepositorySystemSession;

/**
 * Evaluates update policies.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface UpdatePolicyAnalyzer
{

    /**
     * Returns the policy with the shorter update interval.
     * 
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param policy1 A policy to compare, may be {@code null}.
     * @param policy2 A policy to compare, may be {@code null}.
     * @return The policy with the shorter update interval.
     */
    String getEffectiveUpdatePolicy( RepositorySystemSession session, String policy1, String policy2 );

    /**
     * Determines whether the specified modification timestamp satisfies the freshness constraint expressed by the given
     * update policy.
     * 
     * @param session The repository system session during which the check is made, must not be {@code null}.
     * @param lastModified The timestamp to check against the update policy.
     * @param policy The update policy, may be {@code null}.
     * @return {@code true} if the specified timestamp is older than acceptable by the update policy, {@code false}
     *         otherwise.
     */
    boolean isUpdatedRequired( RepositorySystemSession session, long lastModified, String policy );

}
