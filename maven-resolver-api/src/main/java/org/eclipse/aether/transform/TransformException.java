/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.transform;

/**
 * Thrown when transformation failed.
 *
 * @deprecated Without any direct replacement for now. This API is OOM-prone, and also lacks a lot of context about
 * transforming.
 */
@Deprecated
public class TransformException extends Exception {

    /**
     * Creates a new exception with default detail message.
     */
    public TransformException() {
        super("Transformation failed");
    }

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message The detail message, may be {@code null}.
     */
    public TransformException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified cause.
     *
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public TransformException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new exception with the specified detail message and cause.
     *
     * @param message The detail message, may be {@code null}.
     * @param cause   The exception that caused this one, may be {@code null}.
     */
    public TransformException(String message, Throwable cause) {
        super(message, cause);
    }
}
