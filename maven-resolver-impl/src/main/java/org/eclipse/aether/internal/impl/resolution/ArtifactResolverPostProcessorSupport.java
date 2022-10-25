package org.eclipse.aether.internal.impl.resolution;

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

import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.resolution.ArtifactResolverPostProcessor;
import org.eclipse.aether.util.ConfigUtils;

import static java.util.Objects.requireNonNull;

/**
 * Support class to implement {@link ArtifactResolverPostProcessor}.
 *
 * @since TBD
 */
public abstract class ArtifactResolverPostProcessorSupport
        implements ArtifactResolverPostProcessor
{
    private static final String CONFIG_PROP_PREFIX = "aether.artifactResolver.postProcessor.";

    private final String name;

    protected ArtifactResolverPostProcessorSupport( String name )
    {
        this.name = requireNonNull( name );
    }

    /**
     * This implementation will call into underlying code only if enabled.
     */
    @Override
    public void postProcess( RepositorySystemSession session, List<ArtifactResult> artifactResults )
    {
        if ( isEnabled( session ) )
        {
            doPostProcess( session, artifactResults );
        }
    }

    protected abstract void doPostProcess( RepositorySystemSession session, List<ArtifactResult> artifactResults );

    /**
     * To be used by underlying implementations to form configuration property keys properly scoped.
     */
    protected String configPropKey( String name )
    {
        requireNonNull( name );
        return CONFIG_PROP_PREFIX + this.name + "." + name;
    }

    /**
     * Returns {@code true} if session configuration marks this instance as enabled.
     * <p>
     * Default value is {@code false}.
     */
    protected boolean isEnabled( RepositorySystemSession session )
    {
        return ConfigUtils.getBoolean( session, false, CONFIG_PROP_PREFIX + this.name );
    }
}
