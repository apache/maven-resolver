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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

public class DirectoryUtilsTest
{
    @Rule
    public TestName testName = new TestName();

    @Test
    public void expectedCases() throws IOException
    {
        Path tmpDir = Files.createTempDirectory( testName.getMethodName() );
        Path result;

        result = DirectoryUtils.resolveDirectory( "foo", tmpDir, false );
        assertThat( result, equalTo( tmpDir.resolve( "foo" ) ) );

        result = DirectoryUtils.resolveDirectory( "foo/bar", tmpDir, false );
        assertThat( result, equalTo( tmpDir.resolve( "foo/bar" ) ) );

        result = DirectoryUtils.resolveDirectory( "foo/./bar/..", tmpDir, false );
        assertThat( result, equalTo( tmpDir.resolve( "foo" ) ) );

        result = DirectoryUtils.resolveDirectory( "/foo", tmpDir, false );
        assertThat( result, equalTo( Paths.get( "/foo" ) ) );

        result = DirectoryUtils.resolveDirectory( "/foo/bar", tmpDir, false );
        assertThat( result, equalTo( Paths.get( "/foo/bar" ) ) );

        result = DirectoryUtils.resolveDirectory( "/foo/./bar/..", tmpDir, false );
        assertThat( result, equalTo( Paths.get( "/foo" ) ) );
    }

    @Test
    public void existsButIsADirectory() throws IOException
    {
        Path tmpDir = Files.createTempDirectory( testName.getMethodName() );
        Files.createDirectories( tmpDir.resolve( "foo" ) );
        Path result = DirectoryUtils.resolveDirectory( "foo", tmpDir, false );
        assertThat( result, equalTo( tmpDir.resolve( "foo" ) ) );
    }

    @Test
    public void existsButNotADirectory() throws IOException
    {
        Path tmpDir = Files.createTempDirectory( testName.getMethodName() );
        Files.createFile( tmpDir.resolve( "foo" ) );
        try
        {
            DirectoryUtils.resolveDirectory( "foo", tmpDir, false );
        }
        catch ( IOException e )
        {
            assertThat( e.getMessage(), startsWith( "Path exists, but is not a directory:" ) );
        }
    }

    @Test
    public void notExistsAndIsCreated() throws IOException
    {
        Path tmpDir = Files.createTempDirectory( testName.getMethodName() );
        Files.createDirectories( tmpDir.resolve( "foo" ) );
        Path result = DirectoryUtils.resolveDirectory( "foo", tmpDir, true );
        assertThat( result, equalTo( tmpDir.resolve( "foo" ) ) );
        assertThat( Files.isDirectory( tmpDir.resolve( "foo" ) ), equalTo( true ) );
    }
}
