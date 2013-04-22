/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.examples.guice;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.repository.internal.MavenAetherModule;
import org.eclipse.aether.connector.file.FileRepositoryConnectorFactory;
import org.eclipse.aether.connector.wagon.WagonConfigurator;
import org.eclipse.aether.connector.wagon.WagonProvider;
import org.eclipse.aether.connector.wagon.WagonRepositoryConnectorFactory;
import org.eclipse.aether.examples.manual.ManualWagonConfigurator;
import org.eclipse.aether.examples.manual.ManualWagonProvider;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;

class DemoAetherModule
    extends AbstractModule
{

    @Override
    protected void configure()
    {
        install( new MavenAetherModule() );
        // alternatively, use the Guice Multibindings extensions
        bind( RepositoryConnectorFactory.class ).annotatedWith( Names.named( "file" ) ).to( FileRepositoryConnectorFactory.class );
        bind( RepositoryConnectorFactory.class ).annotatedWith( Names.named( "wagon" ) ).to( WagonRepositoryConnectorFactory.class );
        bind( WagonProvider.class ).to( ManualWagonProvider.class );
        bind( WagonConfigurator.class ).to( ManualWagonConfigurator.class );
    }

    @Provides
    @Singleton
    Set<RepositoryConnectorFactory> provideRepositoryConnectorFactories( @Named( "file" ) RepositoryConnectorFactory file,
                                                                         @Named( "wagon" ) RepositoryConnectorFactory wagon )
    {
        Set<RepositoryConnectorFactory> factories = new HashSet<RepositoryConnectorFactory>();
        factories.add( file );
        factories.add( wagon );
        return Collections.unmodifiableSet( factories );
    }

}
