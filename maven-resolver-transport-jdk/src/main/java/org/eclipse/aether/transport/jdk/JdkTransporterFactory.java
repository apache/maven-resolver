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

import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * JDK Transport factory.
 *
 * @since TBD
 */
@Named(JdkTransporterFactory.NAME)
public final class JdkTransporterFactory implements TransporterFactory {
    public static final String NAME = "jdk";

    private float priority = 10.0f;

    @Override
    public float getPriority() {
        return priority;
    }

    public JdkTransporterFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public Transporter newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoTransporterException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");

        if (javaVersion() < 11) {
            LoggerFactory.getLogger(JdkTransporterFactory.class).debug("Needs Java11+ to function");
            throw new NoTransporterException(repository, "JDK Transport needs Java11+");
        }

        if (!"http".equalsIgnoreCase(repository.getProtocol()) && !"https".equalsIgnoreCase(repository.getProtocol())) {
            throw new NoTransporterException(repository, "Only HTTP/HTTPS is supported");
        }

        return new JdkHttpTransporter(session, repository);
    }

    // simple heuristic
    private int javaVersion() {
        try {
            final String version = System.getProperty("java.version", "11" /* default must pass */);
            final int dot = version.indexOf('.');
            final int iphen = version.indexOf('-');
            final int sep = (dot > 0 && dot < iphen || iphen < 0) ? dot : iphen;
            return Integer.parseInt(sep > 0 ? version.substring(0, sep) : version);
        } catch (final NumberFormatException nfe) {
            return 11; // unlikely to be a pre-java 11 version so let it pass
        }
    }
}
