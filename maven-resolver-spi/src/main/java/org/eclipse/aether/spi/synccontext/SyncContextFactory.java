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
package org.eclipse.aether.spi.synccontext;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;

/**
 * A factory to create synchronization contexts. A synchronization context is used to coordinate concurrent access to
 * artifacts or metadata.
 */
public interface SyncContextFactory {

    /**
     * Creates a new synchronization context.
     *
     * @param session The repository session during which the context will be used, must not be {@code null}.
     * @param shared A flag indicating whether access to the artifacts/metadata associated with the new context can be
     *            shared among concurrent readers or whether access needs to be exclusive to the calling thread.
     * @return The synchronization context, never {@code null}.
     * @see RepositorySystem#newSyncContext(RepositorySystemSession, boolean)
     */
    SyncContext newInstance(RepositorySystemSession session, boolean shared);
}
