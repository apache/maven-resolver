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
package org.eclipse.aether.impl;

import org.eclipse.aether.RepositorySystemSession.CloseableSession;

/**
 * Lifecycle managing component for repository system.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 * @since 1.9.0
 */
public interface RepositorySystemLifecycle {
    /**
     * Marks the repository system as ended (shut down): all "on close" handlers will be invoked. This method may be
     * invoked multiple times, only once will execute, subsequent calls will be no-op.
     */
    void systemEnded();

    /**
     * Registers an "on repository system end" handler.
     * <p>
     * Throws if repository system is already shut down.
     */
    void addOnSystemEndedHandler(Runnable handler);

    /**
     * Registers the session for lifecycle tracking: it marks that the passed in session instance is about to start.
     * <p>
     * <em>Same session instance can be started only once.</em>
     *
     * @since 2.0.0
     */
    void sessionStarted(CloseableSession session);

    /**
     * Signals that passed in session was ended, it will not be used anymore. Repository system
     * will invoke the registered handlers for this session, if any. This method throws if the passed in session
     * instance was not passed to method {@link #sessionStarted(CloseableSession)} beforehand.
     * <p>
     * <em>Same session instance can be ended only once.</em>
     *
     * @since 2.0.0
     */
    void sessionEnded(CloseableSession session);

    /**
     * Registers an "on session end" handler.
     * <p>
     * Throws if session was not passed to {@link #sessionStarted(CloseableSession)} beforehand.
     *
     * @since 2.0.0
     */
    void addOnSessionEndedHandle(CloseableSession session, Runnable handler);
}
