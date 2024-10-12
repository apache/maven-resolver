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
package org.eclipse.aether.transport.jdk;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.http.ChecksumExtractor;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporter;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporterFactory;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.transfer.NoTransporterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * JDK Transport factory: on Java 8 it is defunct.
 *
 * @since 2.0.0
 */
@Named(JdkTransporterFactory.NAME)
public final class JdkTransporterFactory implements HttpTransporterFactory {
    public static final String NAME = "jdk";

    private static final Logger LOGGER = LoggerFactory.getLogger(JdkTransporterFactory.class);

    private float priority = 10.0f;

    @Inject
    public JdkTransporterFactory(ChecksumExtractor checksumExtractor, PathProcessor pathProcessor) {
        // this is to equalize all Java version constructors to be same, so Supplier could work across all versions
    }

    @Override
    public float getPriority() {
        return priority;
    }

    public JdkTransporterFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public HttpTransporter newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoTransporterException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");

        LOGGER.debug("Needs Java11+ to function");
        throw new NoTransporterException(repository, "JDK Transport needs Java11+");
    }
}
