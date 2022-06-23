package org.apache.maven.resolver.internal.impl;

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

import java.util.Collection;

import org.apache.maven.resolver.RepositorySystemSession;
import org.apache.maven.resolver.SyncContext;
import org.apache.maven.resolver.artifact.Artifact;
import org.apache.maven.resolver.spi.synccontext.SyncContextFactory;
import org.apache.maven.resolver.metadata.Metadata;

import static java.util.Objects.requireNonNull;

/**
 * 
 */
public class StubSyncContextFactory
    implements SyncContextFactory
{

    public SyncContext newInstance( RepositorySystemSession session, boolean shared )
    {
        requireNonNull( session, "session cannot be null" );
        return new SyncContext()
        {
            public void close()
            {
            }

            public void acquire( Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas )
            {
            }
        };
    }

}
