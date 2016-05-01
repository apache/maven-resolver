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
 * For example, <code>org.eclipse.*</code> would match all artifacts whose group id started with
 * <code>org.eclipse.</code> , and <code>:::*-SNAPSHOT</code> would match all snapshot artifacts.
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
