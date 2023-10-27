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
package org.eclipse.aether.transport.wagon;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;

import static java.util.Objects.requireNonNull;

/**
 * A transporter factory using <a href="http://maven.apache.org/wagon/" target="_blank">Apache Maven Wagon</a>. Note
 * that this factory merely serves as an adapter to the Wagon API and by itself does not provide any transport services
 * unless one or more wagon implementations are registered with the {@link WagonProvider}.
 */
@Named(WagonTransporterFactory.NAME)
public final class WagonTransporterFactory implements TransporterFactory {
    public static final String NAME = "wagon";

    private final WagonProvider wagonProvider;

    private final WagonConfigurator wagonConfigurator;

    private float priority = -1.0f;

    @Inject
    public WagonTransporterFactory(WagonProvider wagonProvider, WagonConfigurator wagonConfigurator) {
        this.wagonProvider = wagonProvider;
        this.wagonConfigurator = wagonConfigurator;
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
    public WagonTransporterFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoTransporterException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");

        return new WagonTransporter(wagonProvider, wagonConfigurator, repository, session);
    }
}
