package org.eclipse.aether.impl;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.function.Consumer;

import org.eclipse.aether.RepositorySystemSession;

/**
 * Component serving as hook for signaling {@link RepositorySystemSession} lifecycle, usually top-level session from
 * application that embeds resolver.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 * @since 1.9.0
 */
public interface RepositorySystemSessionLifecycle
{
    /**
     * Should be invoked with top-level session when it was properly initialized.
     */
    void sessionStarted( RepositorySystemSession session );

    /**
     * Should be invoked with top-level session when it's use ended.
     */
    void sessionEnded( RepositorySystemSession session );

    /**
     * Registers an "on end" handler. TODO: what happens if session is not registered? Ignore this call?
     */
    void addOnSessionEndHandler( RepositorySystemSession session, Consumer<RepositorySystemSession> handler );
}
