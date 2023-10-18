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
package org.eclipse.aether.transport.jetty;

import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;

import static java.util.Objects.requireNonNull;

/**
 * A transporter factory for repositories using the {@code http:} or {@code https:} protocol.
 *
 * @since TBD
 */
@Named(JettyTransporterFactory.NAME)
public final class JettyTransporterFactory implements TransporterFactory {
    public static final String NAME = "jetty";

    private float priority = 15.0f;

    @Override
    public float getPriority() {
        return priority;
    }

    public JettyTransporterFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoTransporterException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");

        if (!"http".equalsIgnoreCase(repository.getProtocol()) && !"https".equalsIgnoreCase(repository.getProtocol())) {
            throw new NoTransporterException(repository);
        }

        return new JettyTransporter(session, repository);
    }
}
