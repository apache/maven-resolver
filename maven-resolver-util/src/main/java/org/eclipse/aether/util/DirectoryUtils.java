package org.eclipse.aether.util;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.aether.RepositorySystemSession;

/**
 * A utility class to calculate (and create) Paths backed by directories using configuration properties from a
 * repository system session and others.
 *
 * @see RepositorySystemSession#getConfigProperties()
 * @see RepositorySystemSession#getLocalRepository()
 * @since TBD
 */
public final class DirectoryUtils
{
    private DirectoryUtils()
    {
        // hide constructor
    }

    /**
     * Creates {@link Path} instance out of passed in {@code name} parameter. May create a directory on resulting path,
     * if not exists. Following outcomes may happen:
     * <ul>
     *     <li>{@code name} is absolute path - results in {@link Path} instance created directly from name.</li>
     *     <li>{@code name} is relative path - results in {@link Path} instance resolved with {@code base} parameter.</li>
     * </ul>
     * Resulting path is being checked is a directory, and if not, it will be created. If resulting path exists but
     * is not a directory, this method will fail.
     *
     * @param name The name to create directory with, cannot be {@code null}.
     * @param base The base {@link Path} to resolve name if it is relative path.
     * @return The {@link Path} instance that is resolved and backed by existing directory.
     * @throws IOException If some IO related errors happens.
     */
    public static Path resolveDirectory( String name, Path base, boolean mayCreate ) throws IOException
    {
        final Path namePath = Paths.get( name );
        final Path result;
        if ( namePath.isAbsolute() )
        {
            result = namePath.normalize();
        }
        else
        {
            result = base.resolve( namePath ).normalize();
        }

        if ( !Files.exists( result ) )
        {
            if ( mayCreate )
            {
                Files.createDirectories( result );
            }
        }
        else if ( !Files.isDirectory( result ) )
        {
            throw new IOException( "Path exists, but is not a directory: " + result );
        }
        return result;
    }
}
