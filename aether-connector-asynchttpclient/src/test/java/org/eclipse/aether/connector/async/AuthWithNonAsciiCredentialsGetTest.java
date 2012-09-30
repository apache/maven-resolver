/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.connector.async;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.sonatype.tests.http.runner.annotations.ConfiguratorList;
import org.sonatype.tests.http.runner.junit.ConfigurationRunner;
import org.sonatype.tests.http.server.api.ServerProvider;

/**
 */
@RunWith( ConfigurationRunner.class )
@ConfiguratorList( "AuthSuiteConfigurator.list" )
public class AuthWithNonAsciiCredentialsGetTest
    extends GetTest
{

    @Override
    public void configureProvider( ServerProvider provider )
    {
        super.configureProvider( provider );
        provider.addUser( "user-non-ascii", "\u00E4\u00DF" );
    }

    @Before
    @Override
    public void before()
        throws Exception
    {
        super.before();

        Authentication auth =
            new AuthenticationBuilder().username( "user-non-ascii" ).password( "\u00E4\u00DF" ).build();
        repository = new RemoteRepository.Builder( repository() ).setAuthentication( auth ).build();
    }

    @Override
    public void testDownloadArtifactWhoseSizeExceedsMaxHeapSize()
        throws Exception
    {
        // this one is slow and doesn't bring anything new to the table in this context so just skip
    }

}
