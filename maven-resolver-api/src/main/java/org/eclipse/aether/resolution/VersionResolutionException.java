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
 * Thrown in case of an unresolvable metaversion.
 */
public class VersionResolutionException extends RepositoryException {

    private final transient VersionResult result;

    /**
     * Creates a new exception with the specified result.
     * Cause will be first selected exception from result, if applicable. All exceptions are added as suppressed as well.
     *
     * @param result The version result at the point the exception occurred, may be {@code null}.
     */
    public VersionResolutionException(VersionResult result) {
        super(getMessage(result), getFirstCause(result));
        if (result != null) {
            result.getExceptions().forEach(this::addSuppressed);
        }
        this.result = result;
    }

    private static String getMessage(VersionResult result) {
        StringBuilder buffer = new StringBuilder(256);
        buffer.append("Failed to resolve version");
        if (result != null) {
            buffer.append(" for ").append(result.getRequest().getArtifact());
            if (!result.getExceptions().isEmpty()) {
                buffer.append(": ")
                        .append(result.getExceptions().iterator().next().getMessage());
            }
        }
        return buffer.toString();
    }

    private static Throwable getFirstCause(VersionResult result) {
        Throwable cause = null;
        if (result != null && !result.getExceptions().isEmpty()) {
            cause = result.getExceptions().get(0);
        }
        return cause;
    }

    /**
     * Creates a new exception with the specified result and detail message.
     * Cause will be first selected exception from result, if applicable. All exceptions are added as suppressed as well.
     *
     * @param result The version result at the point the exception occurred, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public VersionResolutionException(VersionResult result, String message) {
        super(message, getFirstCause(result));
        if (result != null) {
            result.getExceptions().forEach(this::addSuppressed);
        }
        this.result = result;
    }

    /**
     * Creates a new exception with the specified result, detail message and cause.
     * All exceptions are added as suppressed as well.
     *
     * @param result The version result at the point the exception occurred, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public VersionResolutionException(VersionResult result, String message, Throwable cause) {
        super(message, cause);
        if (result != null) {
            result.getExceptions().forEach(this::addSuppressed);
        }
        this.result = result;
    }

    /**
     * Gets the version result at the point the exception occurred. Despite being incomplete, callers might want to use
     * this result to fail gracefully and continue their operation with whatever interim data has been gathered.
     *
     * @return The version result or {@code null} if unknown.
     */
    public VersionResult getResult() {
        return result;
    }
}
