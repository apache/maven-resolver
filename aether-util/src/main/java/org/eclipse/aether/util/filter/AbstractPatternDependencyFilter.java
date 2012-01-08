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
package org.eclipse.aether.util.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionRange;
import org.eclipse.aether.version.VersionScheme;

/**
 */
class AbstractPatternDependencyFilter
    implements DependencyFilter
{

    private final Set<String> patterns = new HashSet<String>();

    private final VersionScheme versionScheme;

    /**
     * Creates a new filter using the specified patterns.
     * 
     * @param patterns The include patterns, may be {@code null} or empty to include no artifacts.
     */
    public AbstractPatternDependencyFilter( final String... patterns )
    {
        this( null, patterns );
    }

    /**
     * Creates a new filter using the specified patterns.
     * 
     * @param versionScheme To be used for parsing versions/version ranges. If {@code null} and pattern specifies a
     *            range no artifact will be included.
     * @param patterns The include patterns, may be {@code null} or empty to include no artifacts.
     */
    public AbstractPatternDependencyFilter( final VersionScheme versionScheme, final String... patterns )
    {
        this( versionScheme, patterns == null ? null : Arrays.asList( patterns ) );
    }

    /**
     * Creates a new filter using the specified patterns.
     * 
     * @param patterns The include patterns, may be {@code null} or empty to include no artifacts.
     */
    public AbstractPatternDependencyFilter( final Collection<String> patterns )
    {
        this( null, patterns );
    }

    /**
     * Creates a new filter using the specified patterns and {@link VersionScheme} .
     * 
     * @param versionScheme To be used for parsing versions/version ranges. If {@code null} and pattern specifies a
     *            range no artifact will be included.
     * @param patterns The include patterns, may be {@code null} or empty to include no artifacts.
     */
    public AbstractPatternDependencyFilter( final VersionScheme versionScheme, final Collection<String> patterns )
    {
        if ( patterns != null )
        {
            this.patterns.addAll( patterns );
        }
        this.versionScheme = versionScheme;
    }

    public boolean accept( final DependencyNode node, List<DependencyNode> parents )
    {
        final Dependency dependency = node.getDependency();
        if ( dependency == null )
        {
            return true;
        }
        return accept( dependency.getArtifact() );
    }

    protected boolean accept( final Artifact artifact )
    {
        for ( final String pattern : patterns )
        {
            final boolean matched = accept( artifact, pattern );
            if ( matched )
            {
                return true;
            }
        }
        return false;
    }

    private boolean accept( final Artifact artifact, final String pattern )
    {
        final String[] tokens =
            new String[] { artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                artifact.getBaseVersion() };

        final String[] patternTokens = pattern.split( ":" );

        // fail immediately if pattern tokens outnumber tokens to match
        boolean matched = ( patternTokens.length <= tokens.length );

        for ( int i = 0; matched && i < patternTokens.length; i++ )
        {
            matched = matches( tokens[i], patternTokens[i] );
        }

        return matched;
    }

    private boolean matches( final String token, final String pattern )
    {
        boolean matches;

        // support full wildcard and implied wildcard
        if ( "*".equals( pattern ) || pattern.length() == 0 )
        {
            matches = true;
        }
        // support contains wildcard
        else if ( pattern.startsWith( "*" ) && pattern.endsWith( "*" ) )
        {
            final String contains = pattern.substring( 1, pattern.length() - 1 );

            matches = ( token.indexOf( contains ) != -1 );
        }
        // support leading wildcard
        else if ( pattern.startsWith( "*" ) )
        {
            final String suffix = pattern.substring( 1, pattern.length() );

            matches = token.endsWith( suffix );
        }
        // support trailing wildcard
        else if ( pattern.endsWith( "*" ) )
        {
            final String prefix = pattern.substring( 0, pattern.length() - 1 );

            matches = token.startsWith( prefix );
        }
        // support versions range
        else if ( pattern.startsWith( "[" ) || pattern.startsWith( "(" ) )
        {
            matches = isVersionIncludedInRange( token, pattern );
        }
        // support exact match
        else
        {
            matches = token.equals( pattern );
        }

        return matches;
    }

    private boolean isVersionIncludedInRange( final String version, final String range )
    {
        if ( versionScheme == null )
        {
            return false;
        }
        else
        {
            try
            {
                final Version parsedVersion = versionScheme.parseVersion( version );
                final VersionRange parsedRange = versionScheme.parseVersionRange( range );

                return parsedRange.containsVersion( parsedVersion );
            }
            catch ( final InvalidVersionSpecificationException e )
            {
                return false;
            }
        }
    }

    @Override
    public boolean equals( final Object obj )
    {
        if ( this == obj )
        {
            return true;
        }

        if ( obj == null || !getClass().equals( obj.getClass() ) )
        {
            return false;
        }

        final AbstractPatternDependencyFilter that = (AbstractPatternDependencyFilter) obj;

        return this.patterns.equals( that.patterns )
            && ( this.versionScheme == null ? that.versionScheme == null
                            : this.versionScheme.equals( that.versionScheme ) );
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + patterns.hashCode();
        hash = hash * 31 + ( ( versionScheme == null ) ? 0 : versionScheme.hashCode() );
        return hash;
    }

}
