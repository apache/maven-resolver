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
package org.eclipse.aether.transfer;

import org.eclipse.aether.RepositoryException;

/**
 * Thrown in case of a checksum failure during an artifact/metadata download.
 */
public class ChecksumFailureException
    extends RepositoryException
{

    private final String expected;

    private final String actual;

    public ChecksumFailureException( String expected, String actual )
    {
        super( "Checksum validation failed, expected " + expected + " but is " + actual );

        this.expected = expected;
        this.actual = actual;
    }

    public ChecksumFailureException( String message )
    {
        super( message );
        expected = actual = "";
    }

    public ChecksumFailureException( Throwable cause )
    {
        super( "Checksum validation failed, could not read expected checksum" + getMessage( ": ", cause ), cause );
        expected = actual = "";
    }

    public ChecksumFailureException( String message, Throwable cause )
    {
        super( message, cause );
        expected = actual = "";
    }

    public String getExpected()
    {
        return expected;
    }

    public String getActual()
    {
        return actual;
    }

}
