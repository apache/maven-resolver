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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.util.ConfigUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Static {@link NameMapper}, always assigns one same name, effectively becoming equivalent to "static" sync context.
 */
@Singleton
@Named( StaticNameMapper.NAME )
public class StaticNameMapper implements NameMapper
{
    public static final String NAME = "static";

    /**
     * Configuration property to pass in static name
     */
    private static final String CONFIG_PROP_NAME = "aether.syncContext.named.static.name";

    private final String name;

    /**
     * Uses string {@code "static"} for the static name
     */
    @Inject
    public StaticNameMapper()
    {
        this( NAME );
    }

    /**
     * Uses passed in non-{@code null} string for the static name
     */
    public StaticNameMapper( final String name )
    {
        this.name = Objects.requireNonNull( name );
    }

    @Override
    public Collection<String> nameLocks( final RepositorySystemSession session,
                                         final Collection<? extends Artifact> artifacts,
                                         final Collection<? extends Metadata> metadatas )
    {
        return Collections.singletonList( ConfigUtils.getString( session, name, CONFIG_PROP_NAME ) );
    }
}
