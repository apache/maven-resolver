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

/**
 * A task to check the existence of a resource in the remote repository. <em>Note:</em> The listener returned from
 * {@link #getListener()} is always a noop given that none of its event methods are relevant in context of this task.
 *
 * @see Transporter#peek(PeekTask)
 */
public final class PeekTask extends TransportTask {

    /**
     * Creates a new task for the specified remote resource.
     *
     * @param location The relative location of the resource in the remote repository, must not be {@code null}.
     */
    public PeekTask(URI location) {
        setLocation(location);
    }

    @Override
    public String toString() {
        return "?? " + getLocation();
    }
}
