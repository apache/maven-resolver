package org.eclipse.aether;

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

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Runtime exception to be thrown when multiple actions were executed and one or more failed. To be used when no
 * fallback on resolver side is needed or is possible.
 *
 * @since TBD
 */
public final class MultiRuntimeException
        extends RuntimeException
{
    private final List<? extends Throwable> throwable;

    private MultiRuntimeException( String message, List<? extends Throwable> throwable )
    {
        super( message );
        this.throwable = throwable;
        for ( Throwable t : throwable )
        {
            addSuppressed( t );
        }
    }

    /**
     * Returns the list of throwable that is wrapped in this exception.
     *
     * @return The list of throwable, never {@code null}.
     */
    public List<? extends Throwable> getThrowable()
    {
        return throwable;
    }

    /**
     * Helper method that receives a (non-null) message and (non-null) list of throwable, and following happens:
     * <ul>
     *     <li>if list is empty - nothing</li>
     *     <li>if list not empty - {@link MultiRuntimeException} is thrown wrapping all elements</li>
     * </ul>
     */
    public static void mayThrow( String message, List<? extends Throwable> throwable )
    {
        requireNonNull( message );
        requireNonNull( throwable );

        if ( !throwable.isEmpty() )
        {
            throw new MultiRuntimeException( message, throwable );
        }
    }
}
