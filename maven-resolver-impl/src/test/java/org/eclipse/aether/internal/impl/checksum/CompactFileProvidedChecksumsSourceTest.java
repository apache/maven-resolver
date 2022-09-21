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

import org.eclipse.aether.util.artifact.ArtifactIdUtils;

public class CompactFileProvidedChecksumsSourceTest extends FileProvidedChecksumsSourceTestSupport
{
    @Override
    protected FileProvidedChecksumsSourceSupport prepareSubject( Path baseDir ) throws IOException
    {
        session.setConfigProperty( "aether.artifactResolver.providedChecksumsSource.file-compact.enabled",
                Boolean.TRUE.toString() );
        // artifact: test:test:2.0 => "foobar"
        {
            Path test = baseDir.resolve( "checksums.sha1" );
            Files.createDirectories( test.getParent() );
            Files.write( test,
                    ( ArtifactIdUtils.toId( ARTIFACT_WITH_CHECKSUM ) + " " + ARTIFACT_PROVIDED_CHECKSUM ).getBytes(
                            StandardCharsets.UTF_8 ) );
        }

        return new CompactFileProvidedChecksumsSource();
    }
}
