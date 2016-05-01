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
package org.eclipse.aether.internal.impl;

import java.util.Iterator;
import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

/**
 * @deprecated To be deleted without replacement.
 */
@Deprecated
public final class CacheUtils
{

    public static <T> boolean eq( T s1, T s2 )
    {
        return s1 != null ? s1.equals( s2 ) : s2 == null;
    }

    public static int hash( Object obj )
    {
        return obj != null ? obj.hashCode() : 0;
    }

    public static int repositoriesHashCode( List<RemoteRepository> repositories )
    {
        int result = 17;
        for ( RemoteRepository repository : repositories )
        {
            result = 31 * result + repositoryHashCode( repository );
        }
        return result;
    }

    private static int repositoryHashCode( RemoteRepository repository )
    {
        int result = 17;
        result = 31 * result + hash( repository.getUrl() );
        return result;
    }

    private static boolean repositoryEquals( RemoteRepository r1, RemoteRepository r2 )
    {
        if ( r1 == r2 )
        {
            return true;
        }

        return eq( r1.getId(), r2.getId() ) && eq( r1.getUrl(), r2.getUrl() )
            && policyEquals( r1.getPolicy( false ), r2.getPolicy( false ) )
            && policyEquals( r1.getPolicy( true ), r2.getPolicy( true ) );
    }

    private static boolean policyEquals( RepositoryPolicy p1, RepositoryPolicy p2 )
    {
        if ( p1 == p2 )
        {
            return true;
        }
        // update policy doesn't affect contents
        return p1.isEnabled() == p2.isEnabled() && eq( p1.getChecksumPolicy(), p2.getChecksumPolicy() );
    }

    public static boolean repositoriesEquals( List<RemoteRepository> r1, List<RemoteRepository> r2 )
    {
        if ( r1.size() != r2.size() )
        {
            return false;
        }

        for ( Iterator<RemoteRepository> it1 = r1.iterator(), it2 = r2.iterator(); it1.hasNext(); )
        {
            if ( !repositoryEquals( it1.next(), it2.next() ) )
            {
                return false;
            }
        }

        return true;
    }

    public static WorkspaceRepository getWorkspace( RepositorySystemSession session )
    {
        WorkspaceReader reader = session.getWorkspaceReader();
        return ( reader != null ) ? reader.getRepository() : null;
    }

    public static ArtifactRepository getRepository( RepositorySystemSession session,
                                                    List<RemoteRepository> repositories, Class<?> repoClass,
                                                    String repoId )
    {
        if ( repoClass != null )
        {
            if ( WorkspaceRepository.class.isAssignableFrom( repoClass ) )
            {
                return session.getWorkspaceReader().getRepository();
            }
            else if ( LocalRepository.class.isAssignableFrom( repoClass ) )
            {
                return session.getLocalRepository();
            }
            else
            {
                for ( RemoteRepository repository : repositories )
                {
                    if ( repoId.equals( repository.getId() ) )
                    {
                        return repository;
                    }
                }
            }
        }
        return null;
    }

}
