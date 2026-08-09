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
package org.eclipse.aether.spi.connector.transport;

import java.net.URI;

import static java.util.Objects.requireNonNull;

/**
 * A transport task.
 *
 * @noextend This class is not intended to be extended by clients.
 */
public abstract class TransportTask {

    static final TransportListener NOOP = new TransportListener() {};

    static final byte[] EMPTY = {};

    private URI location;

    private TransportListener listener = NOOP;

    TransportTask() {
        // hide
    }

    /**
     * Gets the relative location of the affected resource in the remote repository.
     *
     * @return The relative location of the resource, never {@code null}.
     */
    public URI getLocation() {
        return location;
    }

    TransportTask setLocation(URI location) {
        this.location = requireNonNull(location, "location type cannot be null");
        return this;
    }

    /**
     * Gets the listener that is to be notified during the transfer.
     *
     * @return The listener to notify of progress, never {@code null}.
     */
    public TransportListener getListener() {
        return listener;
    }

    /**
     * Sets the listener that is to be notified during the transfer.
     *
     * @param listener The listener to notify of progress, may be {@code null}.
     * @return This task for chaining, never {@code null}.
     */
    TransportTask setListener(TransportListener listener) {
        this.listener = (listener != null) ? listener : NOOP;
        return this;
    }
}
