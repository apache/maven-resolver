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
package org.eclipse.aether.test.util.connector.suite;

import java.util.Map;

import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.test.impl.TestRepositorySystemSession;
import org.junit.After;
import org.junit.Before;

/**
 * Provides the Junit-callback methods to configure the {@link ConnectorTestSuite} per connector.
 */
public abstract class ConnectorTestSuiteSetup
{

    private ConnectorTestSetup connectorSetup = null;

    protected RemoteRepository repository;

    protected TestRepositorySystemSession session;

    private Map<String, Object> context = null;

    private RepositoryConnectorFactory factory;

    /**
     * @param setup The connector-specific callback handler to use.
     */
    public ConnectorTestSuiteSetup( ConnectorTestSetup setup )
    {
        connectorSetup = setup;
        factory = setup.factory();
        try
        {
            context = connectorSetup.beforeClass( session );
        }
        catch ( Exception e )
        {
            throw new IllegalStateException( "Error while running ConnectorTestSetup#beforeClass.", e );
        }
    }

    /**
     * If called for the first time, calls
     * {@link ConnectorTestSetup#beforeClass(org.eclipse.aether.RepositorySystemSession)}. Always calls
     * {@link ConnectorTestSetup#before(org.eclipse.aether.RepositorySystemSession, Map)}.
     */
    @Before
    public void before()
        throws Exception
    {
        session = new TestRepositorySystemSession();
        repository = connectorSetup.before( session, context );
    }

    /**
     * Calls {@link ConnectorTestSetup#after(org.eclipse.aether.RepositorySystemSession, RemoteRepository, Map)}.
     */
    @After
    public void after()
        throws Exception
    {
        connectorSetup.after( session, repository, context );
    }

    /**
     * @return the factory as determined by {@link ConnectorTestSetup#factory()}.
     */
    protected RepositoryConnectorFactory factory()
    {
        return factory;
    }

}
