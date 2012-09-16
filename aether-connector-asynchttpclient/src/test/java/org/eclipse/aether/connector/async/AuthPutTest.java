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

import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.sonatype.tests.http.runner.annotations.ConfiguratorList;
import org.sonatype.tests.http.runner.junit.ConfigurationRunner;

/**
 */
@RunWith( ConfigurationRunner.class )
@ConfiguratorList( "AuthSuiteConfigurator.list" )
public class AuthPutTest
    extends PutTest
{

    @Before
    @Override
    public void before()
        throws Exception
    {
        super.before();

        repository().setAuthentication( new AuthenticationBuilder().username( "user" ).password( "password" ).build() );
    }

}
