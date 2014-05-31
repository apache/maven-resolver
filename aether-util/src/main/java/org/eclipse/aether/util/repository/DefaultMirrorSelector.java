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
package org.eclipse.aether.util.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
     * @param id The identifier of the mirror, may be {@code null}.
     * @param url The URL of the mirror, may be {@code null}.
     * @param type The content type of the mirror, may be {@code null}.
     * @param repositoryManager A flag whether the mirror is a repository manager or a simple server.
     * @param mirrorOfIds The identifier(s) of remote repositories to mirror, may be {@code null}. Multiple identifiers
     *            can be separated by comma (',') and additionally the wildcards "*" and "external:*" can be used to
     *            match all (external) repositories, prefixing a repo id with an exclamation mark allows to express an
     *            exclusion. For example "external:*,!central".
     * @param mirrorOfTypes The content type(s) of remote repositories to mirror, may be {@code null} or empty to match
     *            any content type. Multiple types can be separated by comma (','), the wildcard "*" and the "!"
     *            negation syntax are also supported. For example "*,!p2".
     * @return This selector for chaining, never {@code null}.
     */
    public DefaultMirrorSelector add( String id, String url, String type, boolean repositoryManager,
                                      String mirrorOfIds, String mirrorOfTypes )
    {
        return add( id, url, type, repositoryManager, split( mirrorOfIds ), split( mirrorOfTypes ) );
    }

    /**
     * Adds the specified mirror to this selector.
     * 
     * @param id The identifier of the mirror, may be {@code null}.
     * @param url The URL of the mirror, may be {@code null}.
     * @param type The content type of the mirror, may be {@code null}.
     * @param repositoryManager A flag whether the mirror is a repository manager or a simple server.
     * @param mirrorOfIds The identifier(s) of remote repositories to mirror, may be {@code null}. The wildcards "*" and
     *            "external:*" can be used to match all (external) repositories, prefixing a repo id with an exclamation
     *            mark allows to express an exclusion. For example "external:*", "!central".
     * @param mirrorOfTypes The content type(s) of remote repositories to mirror, may be {@code null} or empty to match
     *            any content type. The wildcard "*" and the "!" negation syntax are also supported. For example "*",
     *            "!p2".
     * @return This selector for chaining, never {@code null}.
     * @since 1.1.0
     */
    public DefaultMirrorSelector add( String id, String url, String type, boolean repositoryManager,
                                      Collection<String> mirrorOfIds, Collection<String> mirrorOfTypes )
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
        if ( !mirrors.isEmpty() )
        {
            String repoId = repository.getId();
            String repoType = repository.getContentType();

            for ( MirrorDef mirror : mirrors )
            {
                if ( mirror.idMatcher.isExactMatch( repoId ) && mirror.typeMatcher.isMatch( repoType ) )
                {
                    return mirror;
                }
            }

            for ( MirrorDef mirror : mirrors )
            {
                if ( mirror.idMatcher.isMatch( repository ) && mirror.typeMatcher.isMatch( repoType ) )
                {
                    return mirror;
                }
            }
        }

        return null;
    }

    static List<String> split( String list )
    {
        List<String> tokens = null;
        if ( list != null )
        {
            tokens = Arrays.asList( list.split( "," ) );
        }
        return tokens;
    }

    static String[] clean( Collection<String> tokens )
    {
        List<String> cleaned = new ArrayList<String>();
        if ( tokens != null )
        {
            for ( String token : tokens )
            {
                if ( token != null && token.length() > 0 )
                {
                    cleaned.add( token );
                }
            }
        }
        return cleaned.toArray( new String[cleaned.size()] );
    }

    static final class IdMatcher
    {

        final String[] mirrorOfIds;

        public IdMatcher( Collection<String> mirrorOfIds )
        {
            this.mirrorOfIds = clean( mirrorOfIds );
        }

        boolean isExactMatch( String repoId )
        {
            return mirrorOfIds.length == 1 && mirrorOfIds[0].equals( repoId );
        }

        boolean isMatch( RemoteRepository repository )
        {
            boolean result = false;
            if ( mirrorOfIds.length <= 0 )
            {
                result = false;
            }
            else
            {
                String repoId = repository.getId();
                for ( String mirrorOfId : mirrorOfIds )
                {
                    if ( mirrorOfId.equals( repoId ) )
                    {
                        result = true;
                        break;
                    }
                    else if ( mirrorOfId.startsWith( "!" ) && mirrorOfId.substring( 1 ).equals( repoId ) )
                    {
                        result = false;
                        break;
                    }
                    else if ( EXTERNAL_WILDCARD.equals( mirrorOfId ) && isExternalRepo( repository ) )
                    {
                        result = true;
                    }
                    else if ( WILDCARD.equals( mirrorOfId ) )
                    {
                        result = true;
                    }
                }
            }
            return result;
        }

        static boolean isExternalRepo( RemoteRepository repository )
        {
            boolean local =
                "localhost".equals( repository.getHost() ) || "127.0.0.1".equals( repository.getHost() )
                    || "file".equalsIgnoreCase( repository.getProtocol() );
            return !local;
        }

    }

    static final class TypeMatcher
    {

        final String[] mirrorOfTypes;

        public TypeMatcher( Collection<String> mirrorOfTypes )
        {
            this.mirrorOfTypes = clean( mirrorOfTypes );
        }

        boolean isMatch( String repoType )
        {
            boolean result = false;
            if ( mirrorOfTypes.length <= 0 )
            {
                result = true;
            }
            else
            {
                for ( String mirrorOfType : mirrorOfTypes )
                {
                    if ( mirrorOfType.equals( repoType ) )
                    {
                        result = true;
                        break;
                    }
                    else if ( mirrorOfType.startsWith( "!" ) && mirrorOfType.substring( 1 ).equals( repoType ) )
                    {
                        result = false;
                        break;
                    }
                    else if ( WILDCARD.equals( mirrorOfType ) )
                    {
                        result = true;
                    }
                }
            }
            return result;
        }

    }

    static final class MirrorDef
    {

        final String id;

        final String url;

        final String type;

        final boolean repositoryManager;

        final IdMatcher idMatcher;

        final TypeMatcher typeMatcher;

        public MirrorDef( String id, String url, String type, boolean repositoryManager,
                          Collection<String> mirrorOfIds, Collection<String> mirrorOfTypes )
        {
            this.id = id;
            this.url = url;
            this.type = type;
            this.repositoryManager = repositoryManager;
            this.idMatcher = new IdMatcher( mirrorOfIds );
            this.typeMatcher = new TypeMatcher( mirrorOfTypes );
        }

    }

}
