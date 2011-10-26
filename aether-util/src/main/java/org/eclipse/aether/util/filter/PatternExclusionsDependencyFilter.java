/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.filter;

import java.util.Collection;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.version.VersionScheme;

/**
 * A simple filter to exclude artifacts from a list of patterns. The artifact pattern syntax is of the form:
 * 
 * <pre>
 * [groupId]:[artifactId]:[extension]:[version]
 * </pre>
 * <p>
 * Where each pattern segment is optional and supports full and partial <code>*</code> wildcards. An empty pattern
 * segment is treated as an implicit wildcard. Version can be a range in case a {@link VersionScheme} is specified.
 * </p>
 * <p>
 * For example, <code>org.eclipse.*</code> would match all artifacts whose group id started with <code>org.eclipse.</code>
 * , and <code>:::*-SNAPSHOT</code> would match all snapshot artifacts.
 * </p>
 */
public final class PatternExclusionsDependencyFilter
    extends AbstractPatternDependencyFilter
{

    /**
     * Creates a new filter using the specified patterns.
     * 
     * @param patterns The exclude patterns, may be {@code null} or empty to exclude no artifacts.
     */
    public PatternExclusionsDependencyFilter( final String... patterns )
    {
        super( patterns );
    }

    /**
     * Creates a new filter using the specified patterns.
     * 
     * @param versionScheme To be used for parsing versions/version ranges. If {@code null} and pattern specifies a
     *            range no artifact will be excluded.
     * @param patterns The exclude patterns, may be {@code null} or empty to exclude no artifacts.
     */
    public PatternExclusionsDependencyFilter( final VersionScheme versionScheme, final String... patterns )
    {
        super( versionScheme, patterns );
    }

    /**
     * Creates a new filter using the specified patterns.
     * 
     * @param patterns The include patterns, may be {@code null} or empty to include no artifacts.
     */
    public PatternExclusionsDependencyFilter( final Collection<String> patterns )
    {
        super( patterns );
    }

    /**
     * Creates a new filter using the specified patterns and {@link VersionScheme} .
     * 
     * @param versionScheme To be used for parsing versions/version ranges. If {@code null} and pattern specifies a
     *            range no artifact will be excluded.
     * @param patterns The exclude patterns, may be {@code null} or empty to exclude no artifacts.
     */
    public PatternExclusionsDependencyFilter( final VersionScheme versionScheme, final Collection<String> patterns )
    {
        super( versionScheme, patterns );
    }

    @Override
    protected boolean accept( Artifact artifact )
    {
        return !super.accept( artifact );
    }

}
