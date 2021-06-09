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
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.RemoteRepository;

import static java.util.Objects.requireNonNull;

/**
 * A simple mirror selector that selects mirrors based on repository identifiers.
 */
public final class DefaultMirrorSelector
    implements MirrorSelector
{

    private static final String WILDCARD = "*";

    private static final String EXTERNAL_WILDCARD = "external:*";

    private static final String EXTERNAL_HTTP_WILDCARD = "external:http:*";

    private final List<MirrorDef> mirrors = new ArrayList<>();

    @Deprecated
    public DefaultMirrorSelector add( String id, String url, String type, boolean repositoryManager,
                                      String mirrorOfIds, String mirrorOfTypes )
    {
        return add( id, url, type, repositoryManager, false, mirrorOfIds, mirrorOfTypes );
    }

    /**
     * Adds the specified mirror to this selector.
     * 
     * @param id The identifier of the mirror, must not be {@code null}.
     * @param url The URL of the mirror, must not be {@code null}.
     * @param type The content type of the mirror, must not be {@code null}.
     * @param repositoryManager A flag whether the mirror is a repository manager or a simple server.
     * @param blocked A flag whether the mirror is blocked from performing any download requests.
     * @param mirrorOfIds The identifier(s) of remote repositories to mirror, must not be {@code null}. Multiple
     *            identifiers can be separated by comma and additionally the wildcards "*", "external:http:*" and
     *            "external:*" can be used to match all (external) repositories, prefixing a repo id with an
     *            exclamation mark allows to express an exclusion. For example "external:*,!central".
     * @param mirrorOfTypes The content type(s) of remote repositories to mirror, may be {@code null} or empty to match
     *            any content type. Similar to the repo id specification, multiple types can be comma-separated, the
     *            wildcard "*" and the "!" negation syntax are supported. For example "*,!p2".
     * @return This selector for chaining, never {@code null}.
     */
    public DefaultMirrorSelector add( String id, String url, String type, boolean repositoryManager, boolean blocked,
                                      String mirrorOfIds, String mirrorOfTypes )
    {
        mirrors.add( new MirrorDef( id, url, type, repositoryManager, blocked, mirrorOfIds, mirrorOfTypes ) );

        return this;
    }

    public RemoteRepository getMirror( RemoteRepository repository )
    {
        requireNonNull( repository, "repository cannot be null" );
        MirrorDef mirror = findMirror( repository );

        if ( mirror == null )
        {
            return null;
        }

        RemoteRepository.Builder builder =
            new RemoteRepository.Builder( mirror.id, repository.getContentType(), mirror.url );

        builder.setRepositoryManager( mirror.repositoryManager );

        builder.setBlocked( mirror.blocked );

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
            for ( MirrorDef mirror : mirrors )
            {
                if ( repoId.equals( mirror.mirrorOfIds ) && matchesType( repository.getContentType(),
                                                                         mirror.mirrorOfTypes ) )
                {
                    return mirror;
                }
            }

            for ( MirrorDef mirror : mirrors )
            {
                if ( matchPattern( repository, mirror.mirrorOfIds ) && matchesType( repository.getContentType(),
                                                                                    mirror.mirrorOfTypes ) )
                {
                    return mirror;
                }
            }
        }

        return null;
    }

    /**
     * This method checks if the pattern matches the originalRepository. Valid patterns:
     * <ul>
     * <li>{@code *} = everything,</li>
     * <li>{@code external:*} = everything not on the localhost and not file based,</li>
     * <li>{@code external:http:*} = any repository not on the localhost using HTTP,</li>
     * <li>{@code repo,repo1} = {@code repo} or {@code repo1},</li>
     * <li>{@code *,!repo1} = everything except {@code repo1}.</li>
     * </ul>
     * 
     * @param repository to compare for a match.
     * @param pattern used for match.
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
                // check for external:http:*
                else if ( EXTERNAL_HTTP_WILDCARD.equals( repo ) && isExternalHttpRepo( repository ) )
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
        boolean local = isLocal( repository.getHost() ) || "file".equalsIgnoreCase( repository.getProtocol() );
        return !local;
    }

    private static boolean isLocal( String host )
    {
        return "localhost".equals( host ) || "127.0.0.1".equals( host );
    }

    /**
     * Checks the URL to see if this repository refers to a non-localhost repository using HTTP.
     * 
     * @param repository The repository to check, must not be {@code null}.
     * @return {@code true} if external, {@code false} otherwise.
     */
    static boolean isExternalHttpRepo( RemoteRepository repository )
    {
        return ( "http".equalsIgnoreCase( repository.getProtocol() )
            || "dav".equalsIgnoreCase( repository.getProtocol() )
            || "dav:http".equalsIgnoreCase( repository.getProtocol() )
            || "dav+http".equalsIgnoreCase( repository.getProtocol() ) )
            && !isLocal( repository.getHost() );
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

        final boolean blocked;

        final String mirrorOfIds;

        final String mirrorOfTypes;

        MirrorDef( String id, String url, String type, boolean repositoryManager, boolean blocked, String mirrorOfIds,
                   String mirrorOfTypes )
        {
            this.id = id;
            this.url = url;
            this.type = type;
            this.repositoryManager = repositoryManager;
            this.blocked = blocked;
            this.mirrorOfIds = mirrorOfIds;
            this.mirrorOfTypes = mirrorOfTypes;
        }

    }

}
