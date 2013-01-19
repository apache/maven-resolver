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
package org.eclipse.aether.util.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A simple mirror selector that selects mirrors based on repository identifiers.
 */
public final class DefaultMirrorSelector
    implements MirrorSelector
{

    private static final String WILDCARD = "*";

    private static final String EXTERNAL_WILDCARD = "external:*";

    private final List<MirrorDef> mirrors = new ArrayList<MirrorDef>();

    /**
     * Adds the specified mirror to this selector.
     * 
     * @param id The identifier of the mirror, must not be {@code null}.
     * @param url The URL of the mirror, must not be {@code null}.
     * @param type The content type of the mirror, must not be {@code null}.
     * @param repositoryManager A flag whether the mirror is a repository manager or a simple server.
     * @param mirrorOfIds The identifier(s) of remote repositories to mirror, must not be {@code null}. Multiple
     *            identifiers can be separated by comma and additionally the wildcards "*" and "external:*" can be used
     *            to match all (external) repositories, prefixing a repo id with an exclamation mark allows to express
     *            an exclusion. For example "external:*,!central".
     * @param mirrorOfTypes The content type(s) of remote repositories to mirror, may be {@code null} or empty to match
     *            any content type. Similar to the repo id specification, multiple types can be comma-separated, the
     *            wildcard "*" and the "!" negation syntax are supported. For example "*,!p2".
     * @return This selector for chaining, never {@code null}.
     */
    public DefaultMirrorSelector add( String id, String url, String type, boolean repositoryManager,
                                      String mirrorOfIds, String mirrorOfTypes )
    {
        mirrors.add( new MirrorDef( id, url, type, repositoryManager, mirrorOfIds, mirrorOfTypes ) );

        return this;
    }

    public RemoteRepository getMirror( RemoteRepository repository )
    {
        MirrorDef mirror = findMirror( repository );

        if ( mirror == null )
        {
            return null;
        }

        RemoteRepository.Builder builder =
            new RemoteRepository.Builder( mirror.id, repository.getContentType(), mirror.url );

        builder.setRepositoryManager( mirror.repositoryManager );

        if ( mirror.type != null && mirror.type.length() > 0 )
        {
            builder.setContentType( mirror.type );
        }

        builder.setSnapshotPolicy( repository.getPolicy( true ) );
        builder.setReleasePolicy( repository.getPolicy( false ) );

        builder.setMirroredRepositories( Collections.singletonList( repository ) );

        return builder.build();
    }

    private MirrorDef findMirror( RemoteRepository repository )
    {
        String repoId = repository.getId();

        if ( repoId != null && !mirrors.isEmpty() )
        {
            for ( int i = 0, n = mirrors.size(); i < n; i++ )
            {
                MirrorDef mirror = mirrors.get( i );
                if ( repoId.equals( mirror.mirrorOfIds )
                    && matchesType( repository.getContentType(), mirror.mirrorOfTypes ) )
                {
                    return mirror;
                }
            }

            for ( int i = 0, n = mirrors.size(); i < n; i++ )
            {
                MirrorDef mirror = mirrors.get( i );
                if ( matchPattern( repository, mirror.mirrorOfIds )
                    && matchesType( repository.getContentType(), mirror.mirrorOfTypes ) )
                {
                    return mirror;
                }
            }
        }

        return null;
    }

    /**
     * This method checks if the pattern matches the originalRepository. Valid patterns: * = everything external:* =
     * everything not on the localhost and not file based. repo,repo1 = repo or repo1 *,!repo1 = everything except repo1
     * 
     * @param repository to compare for a match.
     * @param pattern used for match. Currently only '*' is supported.
     * @return true if the repository is a match to this pattern.
     */
    static boolean matchPattern( RemoteRepository repository, String pattern )
    {
        boolean result = false;
        String originalId = repository.getId();

        // simple checks first to short circuit processing below.
        if ( WILDCARD.equals( pattern ) || pattern.equals( originalId ) )
        {
            result = true;
        }
        else
        {
            // process the list
            String[] repos = pattern.split( "," );
            for ( String repo : repos )
            {
                // see if this is a negative match
                if ( repo.length() > 1 && repo.startsWith( "!" ) )
                {
                    if ( repo.substring( 1 ).equals( originalId ) )
                    {
                        // explicitly exclude. Set result and stop processing.
                        result = false;
                        break;
                    }
                }
                // check for exact match
                else if ( repo.equals( originalId ) )
                {
                    result = true;
                    break;
                }
                // check for external:*
                else if ( EXTERNAL_WILDCARD.equals( repo ) && isExternalRepo( repository ) )
                {
                    result = true;
                    // don't stop processing in case a future segment explicitly excludes this repo
                }
                else if ( WILDCARD.equals( repo ) )
                {
                    result = true;
                    // don't stop processing in case a future segment explicitly excludes this repo
                }
            }
        }
        return result;
    }

    /**
     * Checks the URL to see if this repository refers to an external repository.
     * 
     * @param repository The repository to check, must not be {@code null}.
     * @return {@code true} if external, {@code false} otherwise.
     */
    static boolean isExternalRepo( RemoteRepository repository )
    {
        boolean local =
            "localhost".equals( repository.getHost() ) || "127.0.0.1".equals( repository.getHost() )
                || "file".equalsIgnoreCase( repository.getProtocol() );
        return !local;
    }

    /**
     * Checks whether the types configured for a mirror match with the type of the repository.
     * 
     * @param repoType The type of the repository, may be {@code null}.
     * @param mirrorType The types supported by the mirror, may be {@code null}.
     * @return {@code true} if the types associated with the mirror match the type of the original repository,
     *         {@code false} otherwise.
     */
    static boolean matchesType( String repoType, String mirrorType )
    {
        boolean result = false;

        // simple checks first to short circuit processing below.
        if ( mirrorType == null || mirrorType.length() <= 0 || WILDCARD.equals( mirrorType ) )
        {
            result = true;
        }
        else if ( mirrorType.equals( repoType ) )
        {
            result = true;
        }
        else
        {
            // process the list
            String[] layouts = mirrorType.split( "," );
            for ( String layout : layouts )
            {
                // see if this is a negative match
                if ( layout.length() > 1 && layout.startsWith( "!" ) )
                {
                    if ( layout.substring( 1 ).equals( repoType ) )
                    {
                        // explicitly exclude. Set result and stop processing.
                        result = false;
                        break;
                    }
                }
                // check for exact match
                else if ( layout.equals( repoType ) )
                {
                    result = true;
                    break;
                }
                else if ( WILDCARD.equals( layout ) )
                {
                    result = true;
                    // don't stop processing in case a future segment explicitly excludes this repo
                }
            }
        }

        return result;
    }

    static class MirrorDef
    {

        final String id;

        final String url;

        final String type;

        final boolean repositoryManager;

        final String mirrorOfIds;

        final String mirrorOfTypes;

        public MirrorDef( String id, String url, String type, boolean repositoryManager, String mirrorOfIds,
                          String mirrorOfTypes )
        {
            this.id = id;
            this.url = url;
            this.type = type;
            this.repositoryManager = repositoryManager;
            this.mirrorOfIds = mirrorOfIds;
            this.mirrorOfTypes = mirrorOfTypes;
        }

    }

}
