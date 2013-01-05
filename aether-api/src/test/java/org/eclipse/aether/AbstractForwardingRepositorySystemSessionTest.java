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
package org.eclipse.aether;

import static org.junit.Assert.*;

import java.lang.reflect.Method;

import org.junit.Test;

public class AbstractForwardingRepositorySystemSessionTest
{

    @Test
    public void testAllMethodsImplemented()
        throws Exception
    {
        for ( Method method : RepositorySystemSession.class.getMethods() )
        {
            Method m =
                AbstractForwardingRepositorySystemSession.class.getDeclaredMethod( method.getName(),
                                                                                   method.getParameterTypes() );
            assertNotNull( method.toString(), m );
        }
    }

}
