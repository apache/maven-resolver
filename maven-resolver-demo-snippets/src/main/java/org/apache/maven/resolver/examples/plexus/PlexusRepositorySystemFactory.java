package org.apache.maven.resolver.examples.plexus;

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

import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.eclipse.aether.RepositorySystem;

/**
 * A factory for repository system instances that employs Plexus to wire up the system's components.
 */
public class PlexusRepositorySystemFactory
{

    public static RepositorySystem newRepositorySystem()
    {
        /*
         * Aether's components are equipped with plexus-specific metadata to enable discovery and wiring of components
         * by a Plexus container so this is as easy as looking up the implementation.
         */
        try
        {
            ContainerConfiguration config = new DefaultContainerConfiguration();
            config.setAutoWiring( true );
            config.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
            return new DefaultPlexusContainer( config ).lookup( RepositorySystem.class );
        }
        catch ( Exception e )
        {
            throw new IllegalStateException( "dependency injection failed", e );
        }
    }

}
