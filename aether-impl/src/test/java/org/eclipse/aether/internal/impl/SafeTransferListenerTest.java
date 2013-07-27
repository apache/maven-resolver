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
package org.eclipse.aether.internal.impl;

import static org.junit.Assert.*;

import java.lang.reflect.Method;

import org.eclipse.aether.transfer.TransferListener;
import org.junit.Test;

/**
 */
public class SafeTransferListenerTest
{

    @Test
    public void testAllEventTypesHandled()
        throws Exception
    {
        Class<?> type = SafeTransferListener.class;
        for ( Method method : TransferListener.class.getMethods() )
        {
            assertNotNull( type.getDeclaredMethod( method.getName(), method.getParameterTypes() ) );
        }
    }

}
