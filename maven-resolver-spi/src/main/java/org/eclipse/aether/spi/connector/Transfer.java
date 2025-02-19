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
package org.eclipse.aether.spi.connector;

import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.transfer.TransferListener;

/**
 * An artifact/metadata transfer.
 *
 * @noextend This class is not intended to be extended by clients.
 */
public abstract class Transfer {

    private TransferListener listener;

    private RequestTrace trace;

    Transfer() {
        // hide from public
    }

    /**
     * Gets the exception that occurred during the transfer (if any).
     *
     * @return The exception or {@code null} if the transfer was successful.
     */
    public abstract Exception getException();

    /**
     * Gets the listener that is to be notified during the transfer.
     *
     * @return The transfer listener or {@code null} if none.
     */
    public TransferListener getListener() {
        return listener;
    }

    /**
     * Sets the listener that is to be notified during the transfer.
     *
     * @param listener The transfer listener to notify, may be {@code null} if none.
     * @return This transfer for chaining, never {@code null}.
     */
    Transfer setListener(TransferListener listener) {
        this.listener = listener;
        return this;
    }

    /**
     * Gets the trace information that describes the higher level request/operation in which this transfer is issued.
     *
     * @return The trace information about the higher level operation or {@code null} if none.
     */
    public RequestTrace getTrace() {
        return trace;
    }

    /**
     * Sets the trace information that describes the higher level request/operation in which this transfer is issued.
     *
     * @param trace The trace information about the higher level operation, may be {@code null}.
     * @return This transfer for chaining, never {@code null}.
     */
    Transfer setTrace(RequestTrace trace) {
        this.trace = trace;
        return this;
    }
}
