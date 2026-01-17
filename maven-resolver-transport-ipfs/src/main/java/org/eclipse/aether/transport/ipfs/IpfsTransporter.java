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
package org.eclipse.aether.transport.ipfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * A transporter using {@link IpfsNamespacePublisher} to implement transport features.
 *
 * @since 1.9.26
 */
final class IpfsTransporter extends AbstractTransporter {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final IpfsNamespacePublisher publisher;
    private final boolean publishIpns;

    IpfsTransporter(
            IpfsNamespacePublisher publisher, boolean publishIpns) {
        this.publisher = requireNonNull(publisher);
        this.publishIpns = publishIpns;
    }

    @Override
    public int classify(Throwable error) {
        if (error instanceof ResourceNotFoundException) {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    @Override
    protected void implPeek(PeekTask task) throws Exception {
        publisher.stat(task.getLocation().getPath());
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        String path = task.getLocation().getPath();
        utilGet(task, publisher.get(path), true, publisher.size(path), false);
    }

    @Override
    protected void implPut(PutTask task) throws Exception {
        try (InputStream inputStream = task.newInputStream()) {
            publisher.put(task.getLocation().getPath(), inputStream);
        }
        utilPut(task, OutputStream.nullOutputStream(), true);
    }

    @Override
    protected void implClose() {
        if (publishIpns) {
            try {
                if (!publisher.publishNamespace()) {
                    logger.warn("IPNS publish unsuccessful, see logs above for reasons");
                }
            } catch (IOException e) {
                logger.warn("IPNS publishing failed", e);
            }
        }
    }
}
