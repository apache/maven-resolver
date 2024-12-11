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
package org.eclipse.aether.transfer;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.Temporal;

import org.apache.maven.api.MonotonicTime;
import org.eclipse.aether.RequestTrace;

/**
 * Describes a resource being uploaded or downloaded by the repository system.
 */
public final class TransferResource {

    private final String repositoryId;

    private final String repositoryUrl;

    private final String resourceName;

    private final Object resource;

    private final Path path;

    private final MonotonicTime startTime;

    private final RequestTrace trace;

    private long contentLength = -1L;

    private long resumeOffset;

    /**
     * Creates a new transfer resource with the specified properties.
     *
     * @param repositoryId The ID of the repository used to transfer the resource, may be {@code null} or
     *                     empty if unknown.
     * @param repositoryUrl The base URL of the repository, may be {@code null} or empty if unknown. If not empty, a
     *            trailing slash will automatically be added if missing.
     * @param resourceName The relative path to the resource within the repository, may be {@code null}. A leading slash
     *            (if any) will be automatically removed.
     * @param file The source/target file involved in the transfer, may be {@code null}.
     * @param trace The trace information, may be {@code null}.
     *
     * @since 1.1.0
     * @deprecated Use {@link TransferResource(String, String, String, Path, Object, RequestTrace)} instead.
     */
    @Deprecated
    public TransferResource(
            String repositoryId, String repositoryUrl, String resourceName, File file, RequestTrace trace) {
        this(repositoryId, repositoryUrl, resourceName, file != null ? file.toPath() : null, null, trace);
    }

    /**
     * Creates a new transfer resource with the specified properties.
     *
     * @param repositoryId The ID of the repository used to transfer the resource, may be {@code null} or
     *                     empty if unknown.
     * @param repositoryUrl The base URL of the repository, may be {@code null} or empty if unknown. If not empty, a
     *            trailing slash will automatically be added if missing.
     * @param resourceName The relative path to the resource within the repository, may be {@code null}. A leading slash
     *            (if any) will be automatically removed.
     * @param path The source/target file involved in the transfer, may be {@code null}.
     * @param resource The representation of this resource, may be {@code null}.
     * @param trace The trace information, may be {@code null}.
     *
     * @since 2.0.0
     */
    public TransferResource(
            String repositoryId,
            String repositoryUrl,
            String resourceName,
            Path path,
            Object resource,
            RequestTrace trace) {
        if (repositoryId == null || repositoryId.isEmpty()) {
            this.repositoryId = "";
        } else {
            this.repositoryId = repositoryId;
        }

        if (repositoryUrl == null || repositoryUrl.isEmpty()) {
            this.repositoryUrl = "";
        } else if (repositoryUrl.endsWith("/")) {
            this.repositoryUrl = repositoryUrl;
        } else {
            this.repositoryUrl = repositoryUrl + '/';
        }

        if (resourceName == null || resourceName.isEmpty()) {
            this.resourceName = "";
        } else if (resourceName.startsWith("/")) {
            this.resourceName = resourceName.substring(1);
        } else {
            this.resourceName = resourceName;
        }

        this.path = path;
        this.resource = resource;
        this.trace = trace;
        this.startTime = MonotonicTime.now();
    }

    /**
     * The ID of the repository, e.g., "central".
     *
     * @return The ID of the repository or an empty string if unknown, never {@code null}.
     *
     * @since 1.1.0
     */
    public String getRepositoryId() {
        return repositoryId;
    }

    /**
     * The base URL of the repository, e.g. "https://repo1.maven.org/maven2/". Unless the URL is unknown, it will be
     * terminated by a trailing slash.
     *
     * @return The base URL of the repository or an empty string if unknown, never {@code null}.
     */
    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    /**
     * The path of the resource relative to the repository's base URL, e.g. "org/apache/maven/maven/3.0/maven-3.0.pom".
     *
     * @return The path of the resource, never {@code null}.
     */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * The representation of "resource", if any. The content of this field may be
     * {@link org.eclipse.aether.artifact.Artifact} or {@link org.eclipse.aether.metadata.Metadata} or {@code null}
     * in case of some legacy flow. Preferred way to handle returned value is with {@code instanceof}.
     *
     * @return The representation of this resource, may be {@code null}.
     * @since 2.0.0
     */
    public Object getResource() {
        return resource;
    }

    /**
     * Gets the local file being uploaded or downloaded. When the repository system merely checks for the existence of a
     * remote resource, no local file will be involved in the transfer.
     *
     * @return The source/target file involved in the transfer or {@code null} if none.
     * @deprecated Use {@link #getPath()} instead.
     */
    @Deprecated
    public File getFile() {
        return path != null ? path.toFile() : null;
    }

    /**
     * Gets the local file being uploaded or downloaded. When the repository system merely checks for the existence of a
     * remote resource, no local file will be involved in the transfer.
     *
     * @return The source/target file involved in the transfer or {@code null} if none.
     * @since 2.0.0
     */
    public Path getPath() {
        return path;
    }

    /**
     * The size of the resource in bytes. Note that the size of a resource during downloads might be unknown to the
     * client which is usually the case when transfers employ compression like gzip. In general, the content length is
     * not known until the transfer has {@link TransferListener#transferStarted(TransferEvent) started}.
     *
     * @return The size of the resource in bytes or a negative value if unknown.
     */
    public long getContentLength() {
        return contentLength;
    }

    /**
     * Sets the size of the resource in bytes.
     *
     * @param contentLength The size of the resource in bytes or a negative value if unknown.
     * @return This resource for chaining, never {@code null}.
     */
    public TransferResource setContentLength(long contentLength) {
        this.contentLength = contentLength;
        return this;
    }

    /**
     * Gets the byte offset within the resource from which the download starts. A positive offset indicates a previous
     * download attempt is being resumed, {@code 0} means the transfer starts at the first byte.
     *
     * @return The zero-based index of the first byte being transferred, never negative.
     */
    public long getResumeOffset() {
        return resumeOffset;
    }

    /**
     * Sets the byte offset within the resource at which the download starts.
     *
     * @param resumeOffset The zero-based index of the first byte being transferred, must not be negative.
     * @return This resource for chaining, never {@code null}.
     */
    public TransferResource setResumeOffset(long resumeOffset) {
        if (resumeOffset < 0L) {
            throw new IllegalArgumentException("resume offset cannot be negative");
        }
        this.resumeOffset = resumeOffset;
        return this;
    }

    /**
     * Gets the timestamp when the transfer of this resource was started.
     *
     * @return The timestamp when the transfer of this resource was started.
     * @deprecated use {@link #getStartTime()}
     */
    @Deprecated
    public long getTransferStartTime() {
        return startTime.getWallTime().toEpochMilli();
    }

    /**
     * Gets the timestamp when the transfer of this resource was started.
     *
     * @return The timestamp when the transfer of this resource was started.
     */
    public MonotonicTime getStartTime() {
        return startTime;
    }

    /**
     * Gets the trace information that describes the higher level request/operation during which this resource is
     * transferred.
     *
     * @return The trace information about the higher level operation or {@code null} if none.
     */
    public RequestTrace getTrace() {
        return trace;
    }

    @Override
    public String toString() {
        return getRepositoryUrl() + getResourceName() + " <> " + getPath();
    }
}
