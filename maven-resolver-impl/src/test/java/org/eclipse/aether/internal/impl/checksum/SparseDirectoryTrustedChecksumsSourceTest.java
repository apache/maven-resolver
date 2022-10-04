package org.eclipse.aether.internal.impl.checksum;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.aether.internal.impl.DefaultFileProcessor;
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer;
import org.eclipse.aether.internal.impl.LocalPathComposer;

public class SparseDirectoryTrustedChecksumsSourceTest extends FileTrustedChecksumsSourceTestSupport
{
    @Override
    protected FileTrustedChecksumsSourceSupport prepareSubject( Path basedir ) throws IOException
    {
        session.setConfigProperty( "aether.trustedChecksumsSource.sparse-directory",
                Boolean.TRUE.toString() );
        LocalPathComposer localPathComposer = new DefaultLocalPathComposer();
        // artifact: test:test:2.0 => "foobar"
        {
            Path test = basedir.resolve( localPathComposer
                    .getPathForArtifact( ARTIFACT_WITH_CHECKSUM, false )
                    + "." + checksumAlgorithmFactory.getFileExtension() );
            Files.createDirectories( test.getParent() );
            Files.write( test, ARTIFACT_TRUSTED_CHECKSUM.getBytes( StandardCharsets.UTF_8 ) );
        }

        return new SparseDirectoryTrustedChecksumsSource( new DefaultFileProcessor(), localPathComposer );
    }
}
