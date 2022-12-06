package org.eclipse.aether.util;

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

import java.util.ArrayList;

import org.eclipse.aether.MultiRuntimeException;

import static java.util.Objects.requireNonNull;

/**
 * Helper class when some "multi element" operation needs to happen as one logical step, and any of the step may
 * throw.
 *
 * @param <T>
 * @since 1.9.3
 */
public final class MultiProcessor<T>
{
    /**
     * The processor.
     *
     * @param <T>
     */
    @FunctionalInterface
    public interface ThrowingConsumer<T>
    {
        void accept( T t ) throws Exception;
    }

    private final ThrowingConsumer<T> consumer;

    private final ArrayList<T> elements;

    public MultiProcessor( ThrowingConsumer<T> consumer )
    {
        this.consumer = requireNonNull( consumer );
        this.elements = new ArrayList<>();
    }

    public void addElement( T element )
    {
        requireNonNull( element );
        elements.add( element );
    }

    public void processAll( String message )
    {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for ( T element : elements )
        {
            try
            {
                consumer.accept( element );
            }
            catch ( Exception e )
            {
                exceptions.add( e );
            }
        }
        MultiRuntimeException.mayThrow( message, exceptions );
    }
}
