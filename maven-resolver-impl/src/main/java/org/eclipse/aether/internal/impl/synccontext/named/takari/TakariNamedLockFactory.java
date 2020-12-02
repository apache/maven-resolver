package org.eclipse.aether.internal.impl.synccontext.named.takari;

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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.internal.impl.synccontext.named.SessionAwareNamedLockFactory;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.aether.named.support.NamedLockSupport;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * A {@link SessionAwareNamedLockFactory} that uses same advisory file locking as Takari Local Repository does. Part of
 * code blatantly copies parts of the Takari {@code LockingSyncContext} and {@code DefaultFileManager}.
 *
 * @see <a href="https://github.com/takari/takari-local-repository/blob/master/src/main/java/io/takari/aether/concurrency/LockingSyncContext.java">Takari
 * LockingSyncContext.java</a>
 * @see <a href="https://github.com/takari/takari-local-repository/blob/master/src/main/java/io/takari/filemanager/internal/DefaultFileManager.java">Takari
 * DefaultFileManager.java</a>
 */
@Singleton
@Named( TakariNamedLockFactory.NAME )
public class TakariNamedLockFactory extends FileLockNamedLockFactory implements SessionAwareNamedLockFactory
{
    public static final String NAME = "takari";

    @Override
    public NamedLockSupport getLock( final RepositorySystemSession session, final String name )
    {
        try
        {
            String fileName = new File( new File( session.getLocalRepository().getBasedir(), ".locks" ), name )
                    .getCanonicalFile().getPath();
            return super.getLock( fileName );
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }
    }

    @Override
    public NamedLockSupport getLock( final String filename )
    {
        throw new IllegalStateException( "This factory is session aware" );
    }
}
