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
package org.eclipse.aether.internal.test.util;

import org.eclipse.aether.DefaultRepositorySystemSession;

/**
 * Utility methods to help unit testing.
 */
public class TestUtils
{

    private TestUtils()
    {
        // hide constructor
    }

    /**
     * Creates a new repository session whose local repository manager is initialized with an instance of
     * {@link TestLocalRepositoryManager}.
     */
    public static DefaultRepositorySystemSession newSession()
    {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        session.setLocalRepositoryManager( new TestLocalRepositoryManager() );
        return session;
    }

}
