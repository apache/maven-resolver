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
package org.eclipse.aether.transport.file;

import javax.inject.Named;

import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Paths;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryUriUtils;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;

import static java.util.Objects.requireNonNull;

/**
 * A transporter factory for repositories using the {@code file:} protocol.
 */
@Named(FileTransporterFactory.NAME)
public final class FileTransporterFactory implements TransporterFactory {
    public static final String NAME = "file";

    private float priority;

    /**
     * Creates an (uninitialized) instance of this transporter factory. <em>Note:</em> In case of manual instantiation
     * by clients, the new factory needs to be configured via its various mutators before first use or runtime errors
     * will occur.
     */
    public FileTransporterFactory() {
        // enables default constructor
    }

    @Override
    public float getPriority() {
        return priority;
    }

    /**
     * Sets the priority of this component.
     *
     * @param priority The priority.
     * @return This component for chaining, never {@code null}.
     */
    public FileTransporterFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoTransporterException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");

        // special case in file transport: to support custom FS providers (like JIMFS), we cannot
        // cover "all possible protocols" to throw NoTransporterEx, but we rely on FS rejecting the URI
        FileTransporter.FileOp fileOp = FileTransporter.FileOp.COPY;
        String repositoryUrl = repository.getUrl();
        if (repositoryUrl.startsWith("symlink+")) {
            fileOp = FileTransporter.FileOp.SYMLINK;
            repositoryUrl = repositoryUrl.substring("symlink+".length());
        } else if (repositoryUrl.startsWith("hardlink+")) {
            fileOp = FileTransporter.FileOp.HARDLINK;
            repositoryUrl = repositoryUrl.substring("hardlink+".length());
        }
        try {
            return new FileTransporter(
                    Paths.get(RepositoryUriUtils.toUri(repositoryUrl)).toAbsolutePath(), fileOp);
        } catch (FileSystemNotFoundException | IllegalArgumentException e) {
            throw new NoTransporterException(repository, e);
        }
    }
}
