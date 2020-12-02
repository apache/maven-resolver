package org.eclipse.aether.named.providers;

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
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.named.support.NamedLockFactorySupport;
import org.eclipse.aether.named.support.NamedLockSupport;

/**
 * Named locks factory of {@link FileLockNamedLock}s. This is a bit special implementation, that expects that lock
 * names be valid FS paths.
 */
@Singleton
@Named( FileLockNamedLockFactory.NAME )
public class FileLockNamedLockFactory
    extends NamedLockFactorySupport
{
    public static final String NAME = "file-lock";

    private final ConcurrentHashMap<String, FileChannel> channels;

    public FileLockNamedLockFactory()
    {
        this.channels = new ConcurrentHashMap<>();
    }

    @Override
    protected NamedLockSupport createLock( final String filename )
    {
        Path path = Paths.get( filename );
        FileChannel fileChannel = channels.computeIfAbsent( filename, k ->
        {
            try
            {
                Files.createDirectories( path.getParent() );
                return FileChannel.open(
                        path,
                        StandardOpenOption.READ, StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE
                );
            }
            catch ( IOException e )
            {
                throw new UncheckedIOException( path.toString(), e );
            }
        } );
        return new FileLockNamedLock( filename, fileChannel, this );
    }

    @Override
    protected void destroyLock( final NamedLockSupport lock )
    {
        try
        {
            FileChannel fileChannel = channels.remove( lock.name() );
            if ( fileChannel != null )
            {
                try
                {
                    fileChannel.close();
                }
                catch ( IOException e )
                {
                    throw new UncheckedIOException( e );
                }
            }
            else
            {
                log.warn( "No FileChannel for lock named '{}'", lock.name() );
            }
        }
        finally
        {
            super.destroyLock( lock );
        }
    }
}
