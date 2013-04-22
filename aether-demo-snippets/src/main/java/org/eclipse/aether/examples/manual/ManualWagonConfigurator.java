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
package org.eclipse.aether.examples.manual;

import org.apache.maven.wagon.Wagon;
import org.eclipse.aether.connector.wagon.WagonConfigurator;

public class ManualWagonConfigurator
    implements WagonConfigurator
{

    public void configure( Wagon wagon, Object configuration )
        throws Exception
    {
        // no-op
    }

}
