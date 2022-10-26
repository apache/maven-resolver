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
import java.nio.file.StandardCopyOption;

import static java.util.Objects.requireNonNull;

/**
 * A utility class to write files.
 *
 * @since 1.9.0
 */
public final class FileUtils
{
    private FileUtils()
    {
        // hide constructor
    }

    /**
     * A file writer, that accepts a {@link Path} to write some content to.
     */
    @FunctionalInterface
    public interface FileWriter
    {
        void write( Path path ) throws IOException;
    }

    /**
     * Writes file without backup.
     *
     * @param target   that is the target file (must be file, the path must have parent).
     * @param writer   the writer that will accept a {@link Path} to write content to.
     * @throws IOException if at any step IO problem occurs.
     */
    public static void writeFile( Path target, FileWriter writer ) throws IOException
    {
        writeFile( target, writer, false );
    }

    /**
     * Writes file with backup copy (appends ".bak" extension).
     *
     * @param target   that is the target file (must be file, the path must have parent).
     * @param writer   the writer that will accept a {@link Path} to write content to.
     * @throws IOException if at any step IO problem occurs.
     */
    public static void writeFileWithBackup( Path target, FileWriter writer ) throws IOException
    {
        writeFile( target, writer, true );
    }

    /**
     * Utility method to write out file to disk in "atomic" manner, with optional backups (".bak") if needed. This
     * ensures that no other thread or process will be able to read not fully written files. Finally, this methos
     * may create the needed parent directories, if the passed in target parents does not exist.
     *
     * @param target   that is the target file (must be an existing or non-existing file, the path must have parent).
     * @param writer   the writer that will accept a {@link Path} to write content to.
     * @param doBackup if {@code true}, and target file is about to be overwritten, a ".bak" file with old contents will
     *                 be created/overwritten.
     * @throws IOException if at any step IO problem occurs.
     */
    private static void writeFile( Path target, FileWriter writer, boolean doBackup ) throws IOException
    {
        requireNonNull( target, "target is null" );
        requireNonNull( writer, "writer is null" );
        Path parent = requireNonNull( target.getParent(), "target must have parent" );
        Path temp = null;

        Files.createDirectories( parent );
        try
        {
            temp = Files.createTempFile( parent, "writer", "tmp" );
            writer.write( temp );
            if ( doBackup && Files.isRegularFile( target ) )
            {
                Files.copy( target, parent.resolve( target.getFileName() + ".bak" ),
                        StandardCopyOption.REPLACE_EXISTING );
            }
            Files.move( temp, target, StandardCopyOption.ATOMIC_MOVE );
        }
        finally
        {
            if ( temp != null )
            {
                Files.deleteIfExists( temp );
            }
        }
    }
}
