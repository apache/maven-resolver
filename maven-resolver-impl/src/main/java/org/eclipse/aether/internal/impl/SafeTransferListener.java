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
package org.eclipse.aether.internal.impl;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

class SafeTransferListener extends AbstractTransferListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SafeTransferListener.class);

    private final TransferListener listener;

    public static TransferListener wrap(RepositorySystemSession session) {
        TransferListener listener = session.getTransferListener();
        if (listener == null) {
            return null;
        }
        return new SafeTransferListener(listener);
    }

    protected SafeTransferListener(RepositorySystemSession session) {
        this(session.getTransferListener());
    }

    private SafeTransferListener(TransferListener listener) {
        this.listener = listener;
    }

    private void logError(TransferEvent event, Throwable e) {
        LOGGER.debug(
                "Failed to dispatch transfer event '{}' to {}",
                event,
                listener.getClass().getCanonicalName(),
                e);
    }

    @Override
    public void transferInitiated(TransferEvent event) throws TransferCancelledException {
        requireNonNull(event, "event cannot be null");
        if (listener != null) {
            try {
                listener.transferInitiated(event);
            } catch (RuntimeException | LinkageError e) {
                logError(event, e);
            }
        }
    }

    @Override
    public void transferStarted(TransferEvent event) throws TransferCancelledException {
        requireNonNull(event, "event cannot be null");
        if (listener != null) {
            try {
                listener.transferStarted(event);
            } catch (RuntimeException | LinkageError e) {
                logError(event, e);
            }
        }
    }

    @Override
    public void transferProgressed(TransferEvent event) throws TransferCancelledException {
        requireNonNull(event, "event cannot be null");
        if (listener != null) {
            try {
                listener.transferProgressed(event);
            } catch (RuntimeException | LinkageError e) {
                logError(event, e);
            }
        }
    }

    @Override
    public void transferCorrupted(TransferEvent event) throws TransferCancelledException {
        requireNonNull(event, "event cannot be null");
        if (listener != null) {
            try {
                listener.transferCorrupted(event);
            } catch (RuntimeException | LinkageError e) {
                logError(event, e);
            }
        }
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        requireNonNull(event, "event cannot be null");
        if (listener != null) {
            try {
                listener.transferSucceeded(event);
            } catch (RuntimeException | LinkageError e) {
                logError(event, e);
            }
        }
    }

    @Override
    public void transferFailed(TransferEvent event) {
        requireNonNull(event, "event cannot be null");
        if (listener != null) {
            try {
                listener.transferFailed(event);
            } catch (RuntimeException | LinkageError e) {
                logError(event, e);
            }
        }
    }
}
