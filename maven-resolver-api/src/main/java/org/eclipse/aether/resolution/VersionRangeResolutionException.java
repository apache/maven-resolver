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
 * Thrown in case of an unparseable or unresolvable version range.
 */
public class VersionRangeResolutionException extends RepositoryException {

    private final transient VersionRangeResult result;

    /**
     * Creates a new exception with the specified result.
     *
     * @param result The version range result at the point the exception occurred, may be {@code null}.
     */
    public VersionRangeResolutionException(VersionRangeResult result) {
        super(getMessage(result), getCause(result));
        this.result = result;
    }

    private static String getMessage(VersionRangeResult result) {
        StringBuilder buffer = new StringBuilder(256);
        buffer.append("Failed to resolve version range");
        if (result != null) {
            buffer.append(" for ").append(result.getRequest().getArtifact());
            if (!result.getExceptions().isEmpty()) {
                buffer.append(": ")
                        .append(result.getExceptions().iterator().next().getMessage());
            }
        }
        return buffer.toString();
    }

    private static Throwable getCause(VersionRangeResult result) {
        Throwable cause = null;
        if (result != null && !result.getExceptions().isEmpty()) {
            cause = result.getExceptions().get(0);
        }
        return cause;
    }

    /**
     * Creates a new exception with the specified result and detail message.
     *
     * @param result The version range result at the point the exception occurred, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public VersionRangeResolutionException(VersionRangeResult result, String message) {
        super(message);
        this.result = result;
    }

    /**
     * Creates a new exception with the specified result, detail message and cause.
     *
     * @param result The version range result at the point the exception occurred, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public VersionRangeResolutionException(VersionRangeResult result, String message, Throwable cause) {
        super(message, cause);
        this.result = result;
    }

    /**
     * Gets the version range result at the point the exception occurred. Despite being incomplete, callers might want
     * to use this result to fail gracefully and continue their operation with whatever interim data has been gathered.
     *
     * @return The version range result or {@code null} if unknown.
     */
    public VersionRangeResult getResult() {
        return result;
    }
}
