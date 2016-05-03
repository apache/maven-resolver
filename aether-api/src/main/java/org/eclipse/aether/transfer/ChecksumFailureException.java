package org.eclipse.aether.transfer;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.eclipse.aether.RepositoryException;

/**
 * Thrown in case of a checksum failure during an artifact/metadata download.
 */
public class ChecksumFailureException
    extends RepositoryException
{

    private final String expected;

    private final String actual;

    private final boolean retryWorthy;

    /**
     * Creates a new exception with the specified expected and actual checksum. The resulting exception is
     * {@link #isRetryWorthy() retry-worthy}.
     * 
     * @param expected The expected checksum as declared by the hosting repository, may be {@code null}.
     * @param actual The actual checksum as computed from the local bytes, may be {@code null}.
     */
    public ChecksumFailureException( String expected, String actual )
    {
        super( "Checksum validation failed, expected " + expected + " but is " + actual );
        this.expected = expected;
        this.actual = actual;
        retryWorthy = true;
    }

    /**
     * Creates a new exception with the specified detail message. The resulting exception is not
     * {@link #isRetryWorthy() retry-worthy}.
     * 
     * @param message The detail message, may be {@code null}.
     */
    public ChecksumFailureException( String message )
    {
        this( false, message, null );
    }

    /**
     * Creates a new exception with the specified cause. The resulting exception is not {@link #isRetryWorthy()
     * retry-worthy}.
     * 
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public ChecksumFailureException( Throwable cause )
    {
        this( "Checksum validation failed" + getMessage( ": ", cause ), cause );
    }

    /**
     * Creates a new exception with the specified detail message and cause. The resulting exception is not
     * {@link #isRetryWorthy() retry-worthy}.
     * 
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public ChecksumFailureException( String message, Throwable cause )
    {
        this( false, message, cause );
    }

    /**
     * Creates a new exception with the specified retry flag, detail message and cause.
     * 
     * @param retryWorthy {@code true} if the exception is retry-worthy, {@code false} otherwise.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public ChecksumFailureException( boolean retryWorthy, String message, Throwable cause )
    {
        super( message, cause );
        expected = actual = "";
        this.retryWorthy = retryWorthy;
    }

    /**
     * Gets the expected checksum for the downloaded artifact/metadata.
     * 
     * @return The expected checksum as declared by the hosting repository or {@code null} if unknown.
     */
    public String getExpected()
    {
        return expected;
    }

    /**
     * Gets the actual checksum for the downloaded artifact/metadata.
     * 
     * @return The actual checksum as computed from the local bytes or {@code null} if unknown.
     */
    public String getActual()
    {
        return actual;
    }

    /**
     * Indicates whether the corresponding download is retry-worthy.
     * 
     * @return {@code true} if retrying the download might solve the checksum failure, {@code false} if the checksum
     *         failure is non-recoverable.
     */
    public boolean isRetryWorthy()
    {
        return retryWorthy;
    }

}
