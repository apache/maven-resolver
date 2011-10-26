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
package org.eclipse.aether;

import static org.junit.Assert.*;

import java.lang.reflect.Method;

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryListener;
import org.junit.Test;

/**
 */
public class AbstractRepositoryListenerTest
{

    @Test
    public void testAllEventTypesHandled()
        throws Exception
    {
        for ( Method method : RepositoryListener.class.getMethods() )
        {
            assertNotNull( AbstractRepositoryListener.class.getDeclaredMethod( method.getName(),
                                                                               method.getParameterTypes() ) );
        }
    }

}
