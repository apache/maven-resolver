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
package org.eclipse.aether.transport.classpath;

import java.io.IOException;

/**
 * Special exception type used instead of {@code FileNotFoundException} to avoid misinterpretation of errors unrelated
 * to the remote resource.
 */
class ResourceNotFoundException
    extends IOException
{

    public ResourceNotFoundException( String message )
    {
        super( message );
    }

}
