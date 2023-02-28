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

import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.RepositoryOfflineException;

/**
 * Thrown in case of a unresolvable artifacts.
 */
public class ArtifactResolutionException extends RepositoryException {

    private final transient List<ArtifactResult> results;

    /**
     * Creates a new exception with the specified results.
     *
     * @param results The resolution results at the point the exception occurred, may be {@code null}.
     */
    public ArtifactResolutionException(List<ArtifactResult> results) {
        super(getMessage(results), getCause(results));
        this.results = (results != null) ? results : Collections.<ArtifactResult>emptyList();
    }

    /**
     * Creates a new exception with the specified results and detail message.
     *
     * @param results The resolution results at the point the exception occurred, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public ArtifactResolutionException(List<ArtifactResult> results, String message) {
        super(message, getCause(results));
        this.results = (results != null) ? results : Collections.<ArtifactResult>emptyList();
    }

    /**
     * Creates a new exception with the specified results, detail message and cause.
     *
     * @param results The resolution results at the point the exception occurred, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public ArtifactResolutionException(List<ArtifactResult> results, String message, Throwable cause) {
        super(message, cause);
        this.results = (results != null) ? results : Collections.<ArtifactResult>emptyList();
    }

    /**
     * Gets the resolution results at the point the exception occurred. Despite being incomplete, callers might want to
     * use these results to fail gracefully and continue their operation with whatever interim data has been gathered.
     *
     * @return The resolution results or {@code null} if unknown.
     */
    public List<ArtifactResult> getResults() {
        return results;
    }

    /**
     * Gets the first result from {@link #getResults()}. This is a convenience method for cases where callers know only
     * a single result/request is involved.
     *
     * @return The (first) resolution result or {@code null} if none.
     */
    public ArtifactResult getResult() {
        return (results != null && !results.isEmpty()) ? results.get(0) : null;
    }

    private static String getMessage(List<? extends ArtifactResult> results) {
        StringBuilder buffer = new StringBuilder(256);

        buffer.append("The following artifacts could not be resolved: ");

        int unresolved = 0;

        String sep = "";
        for (ArtifactResult result : results) {
            if (!result.isResolved()) {
                unresolved++;
                buffer.append(sep);
                buffer.append(result.getRequest().getArtifact());
                LocalArtifactResult localResult = result.getLocalArtifactResult();
                if (localResult != null) {
                    buffer.append(" (");
                    if (localResult.getFile() != null) {
                        buffer.append("present");
                        if (!localResult.isAvailable()) {
                            buffer.append(", but unavailable");
                        }
                    } else {
                        buffer.append("absent");
                    }
                    buffer.append(")");
                }
                sep = ", ";
            }
        }

        Throwable cause = getCause(results);
        if (cause != null) {
            buffer.append(": ").append(cause.getMessage());
        }

        return buffer.toString();
    }

    private static Throwable getCause(List<? extends ArtifactResult> results) {
        for (ArtifactResult result : results) {
            if (!result.isResolved()) {
                Throwable notFound = null, offline = null;
                for (Throwable t : result.getExceptions()) {
                    if (t instanceof ArtifactNotFoundException) {
                        if (notFound == null) {
                            notFound = t;
                        }
                        if (offline == null && t.getCause() instanceof RepositoryOfflineException) {
                            offline = t;
                        }
                    } else {
                        return t;
                    }
                }
                if (offline != null) {
                    return offline;
                }
                if (notFound != null) {
                    return notFound;
                }
            }
        }
        return null;
    }
}
