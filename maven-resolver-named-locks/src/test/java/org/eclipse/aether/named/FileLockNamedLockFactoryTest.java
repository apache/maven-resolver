package org.eclipse.aether.named;

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

import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.junit.BeforeClass;

public class FileLockNamedLockFactoryTest
    extends NamedLockFactoryTestSupport {

    @BeforeClass
    public static void createNamedLockFactory() throws IOException {
        String path = System.getProperty( "java.io.tmpdir" );
        Files.createDirectories(Paths.get(path) ); // hack for surefire: sets the property but directory does not exist

        Path baseDir = Files.createTempDirectory( null );
        namedLockFactory = new FileLockNamedLockFactory() {
            @Override
            protected Path resolveName(final String name) {
                return baseDir.resolve( name );
            }
        };
    }
}
