/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.connector.async;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sonatype.tests.http.runner.junit.ConfigurationRunner;
import org.sonatype.tests.http.server.api.ServerProvider;
import org.sonatype.tests.http.server.jetty.behaviour.Pause;

/**
 */
@RunWith( ConfigurationRunner.class )
public class TimeoutTest
    extends AsyncConnectorSuiteConfiguration
{

    @Override
    public void configureProvider( ServerProvider provider )
    {
        provider.addBehaviour( "/repo/*", new Pause( 100000 ) );
    }

    @Test( timeout = 3000 )
    public void testRequestTimeout()
        throws Exception
    {
        Map<String, Object> configProps = new HashMap<String, Object>();
        configProps.put( ConfigurationProperties.CONNECT_TIMEOUT, "60000" );
        configProps.put( ConfigurationProperties.REQUEST_TIMEOUT, "1000" );
        session().setConfigProperties( configProps );

        File f = TestFileUtils.createTempFile( "" );
        Artifact a = artifact( "foo" );

        ArtifactDownload down = new ArtifactDownload( a, null, f, RepositoryPolicy.CHECKSUM_POLICY_FAIL );
        Collection<? extends ArtifactDownload> downs = Arrays.asList( down );
        connector().get( downs, null );

        assertNotNull( down.getException() );
    }

}
