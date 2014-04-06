/*******************************************************************************
 * Copyright (c) 2012, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.impl.guice;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.Set;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.StubArtifactDescriptorReader;
import org.eclipse.aether.impl.StubVersionRangeResolver;
import org.eclipse.aether.impl.StubVersionResolver;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;

public class AetherModuleTest
{

    @Test
    public void testModuleCompleteness()
    {
        assertNotNull( Guice.createInjector( new SystemModule() ).getInstance( RepositorySystem.class ) );
    }

    static class SystemModule
        extends AbstractModule
    {

        @Override
        protected void configure()
        {
            install( new AetherModule() );
            bind( ArtifactDescriptorReader.class ).to( StubArtifactDescriptorReader.class );
            bind( VersionRangeResolver.class ).to( StubVersionRangeResolver.class );
            bind( VersionResolver.class ).to( StubVersionResolver.class );
        }

        @Provides
        public Set<MetadataGeneratorFactory> metadataGeneratorFactories()
        {
            return Collections.emptySet();
        }

        @Provides
        public Set<RepositoryConnectorFactory> repositoryConnectorFactories()
        {
            return Collections.emptySet();
        }

        @Provides
        public Set<TransporterFactory> transporterFactories()
        {
            return Collections.emptySet();
        }

    }

}
