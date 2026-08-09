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
package org.eclipse.aether.resolution;

import org.eclipse.aether.RepositoryException;

/**
 * Thrown in case of a unresolvable dependencies.
 */
public class DependencyResolutionException extends RepositoryException {

    private final transient DependencyResult result;

    /**
     * Creates a new exception with the specified result and cause.
     *
     * @param result The dependency result at the point the exception occurred, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public DependencyResolutionException(DependencyResult result, Throwable cause) {
        super(getMessage(cause), cause);
        this.result = result;
    }

    /**
     * Creates a new exception with the specified result, detail message and cause.
     *
     * @param result The dependency result at the point the exception occurred, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public DependencyResolutionException(DependencyResult result, String message, Throwable cause) {
        super(message, cause);
        this.result = result;
    }

    private static String getMessage(Throwable cause) {
        String msg = null;
        if (cause != null) {
            msg = cause.getMessage();
        }
        if (msg == null || msg.isEmpty()) {
            msg = "Could not resolve transitive dependencies";
        }
        return msg;
    }

    /**
     * Gets the dependency result at the point the exception occurred. Despite being incomplete, callers might want to
     * use this result to fail gracefully and continue their operation with whatever interim data has been gathered.
     *
     * @return The dependency result or {@code null} if unknown.
     */
    public DependencyResult getResult() {
        return result;
    }
}
