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

import java.util.Collection;

import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

import static java.util.Objects.requireNonNull;

/**
 * A {@link SyncContext} wrapper that delegates {@link #close()} to the underlying context at most once, allowing the
 * context to be managed with try-with-resources while it is also closed explicitly at an earlier point (e.g. the
 * shared context must be closed before the resolver switches to the exclusive one). Closing the underlying context
 * twice is not guaranteed to be harmless by the {@link SyncContext} contract.
 */
final class CloseOnceSyncContext implements SyncContext {

    private final SyncContext delegate;
    private boolean closed;

    CloseOnceSyncContext(SyncContext delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public void acquire(Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas) {
        if (closed) {
            throw new IllegalStateException("sync context is already closed");
        }
        delegate.acquire(artifacts, metadatas);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            delegate.close();
        }
    }
}
