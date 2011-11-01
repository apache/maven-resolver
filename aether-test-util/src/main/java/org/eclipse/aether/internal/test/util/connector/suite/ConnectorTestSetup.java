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
package org.eclipse.aether.internal.test.util.connector.suite;

import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;

/**
 * The callback handler used to configure {@link ConnectorTestSuite}.
 * <p>
 * The specified methods have the same meaning as the corresponding JUnit4-annotations.
 * 
 * @see org.eclipse.aether.internal.test.util.connector.suite.ConnectorTestSetup.AbstractConnectorTestSetup
 */
public interface ConnectorTestSetup
{

    /**
     * This method is called before each test run.
     * 
     * @param session The session used for the test bundle.
     * @param context The context provided by {@link #beforeClass(RepositorySystemSession)}.
     * @return The repository to use in the next test.
     */
    public RemoteRepository before( RepositorySystemSession session, Map<String, Object> context )
        throws Exception;

    /**
     * This method is called before the first test of the bundle is executed.
     * 
     * @param session The session used for the test bundle.
     * @return A map acting as a context for the current test bundle run. This context will not be used by the test
     *         cases in the suite, and is only used to provide context for the before/after/afterClass-method calls.
     *         This might be used to save setup-specific values (port numbers, directories to clean up after tests, ...)
     */
    public Map<String, Object> beforeClass( RepositorySystemSession session )
        throws Exception;

    /**
     * This method is called after each test of the bundle is executed. Repositories should be cleaned after each test,
     * as previous uploads may influence test results.
     * 
     * @param session The session used for the test bundle.
     * @param context The context provided by {@link #beforeClass(RepositorySystemSession)}.
     * @param repository the repository used in the test run.
     */
    public void after( RepositorySystemSession session, RemoteRepository repository, Map<String, Object> context )
        throws Exception;

    /**
     * @return the factory to use for the tests.
     */
    public RepositoryConnectorFactory factory();

    /**
     * Empty implementation of {@link ConnectorTestSetup}.
     */
    public static abstract class AbstractConnectorTestSetup
        implements ConnectorTestSetup
    {

        public Map<String, Object> beforeClass( RepositorySystemSession session )
            throws Exception
        {
            return null;
        }

        public void after( RepositorySystemSession session, RemoteRepository repository, Map<String, Object> context )
            throws Exception
        {
        }

    }

}
