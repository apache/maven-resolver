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
package org.eclipse.aether.transport.wagon;

import org.apache.maven.wagon.Wagon;

/**
 */
public class StreamWagonTransporterTest
    extends AbstractWagonTransporterTest
{

    @Override
    protected Wagon newWagon()
    {
        return new MemStreamWagon();
    }

}
