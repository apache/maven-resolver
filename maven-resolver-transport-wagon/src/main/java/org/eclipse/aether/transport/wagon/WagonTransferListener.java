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

import java.nio.ByteBuffer;

import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.transfer.TransferCancelledException;

/**
 * A wagon transfer listener that forwards events to a transport listener.
 */
final class WagonTransferListener extends AbstractTransferListener {

    private final TransportListener listener;

    WagonTransferListener(TransportListener listener) {
        this.listener = listener;
    }

    @Override
    public void transferStarted(TransferEvent event) {
        try {
            listener.transportStarted(0, event.getResource().getContentLength());
        } catch (TransferCancelledException e) {
            /*
             * NOTE: Wagon transfers are not freely abortable. In particular, aborting from
             * AbstractWagon.fire(Get|Put)Started() would result in unclosed streams so we avoid this case.
             */
        }
    }

    @Override
    public void transferProgress(TransferEvent event, byte[] buffer, int length) {
        try {
            listener.transportProgressed(ByteBuffer.wrap(buffer, 0, length));
        } catch (TransferCancelledException e) {
            throw new WagonCancelledException(e);
        }
    }
}
