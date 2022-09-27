package org.eclipse.aether.internal.impl.synccontext.named;

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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.junit.Before;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Simple support class for {@link NameMapper} implementation UTs.
 */
public abstract class NameMapperTestSupport
{
    protected String basedir;

    protected HashMap<String, Object> configProperties;

    protected RepositorySystemSession session;

    @Before
    public void before() throws IOException
    {
        basedir = new File("/home/maven/.m2/repository").getCanonicalPath();
        configProperties = new HashMap<>();

        LocalRepository localRepository = new LocalRepository( new File( basedir ) );
        session = mock( RepositorySystemSession.class );
        when( session.getConfigProperties() ).thenReturn( configProperties );
        when( session.getLocalRepository() ).thenReturn( localRepository );
    }
}
