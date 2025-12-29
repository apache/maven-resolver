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
package org.eclipse.aether.spi.locking;

import java.util.Optional;

import org.eclipse.aether.RepositorySystemSession;

/**
 * A factory to create {@link LockingInhibitor} instances, that are capable to augment Resolver locking subsystem and
 * prevent locking to happen on certain resources.
 * <p>
 * <em>Warning: locking inhibition should be applied ONLY to resources that do not conflict on disk (i.e. local
 * repository) and are NOT produced/written or altered in any way by Resolver, merely cached and read. Hence, despite
 * artifact locking inhibition is given as option, it should never happen in fact, as aforementioned conditions
 * never stand for them. On the other hand, good examples of resources may be needing locking inhibition are
 * archetype catalogs and RRF prefix files, as both are metadata, hence their remotely fetched cache entries do not
 * conflict locally, furthermore both are produced by remote entities only, and are just cached and read by Maven.</em>
 *
 * @since 2.0.14
 */
public interface LockingInhibitorFactory {
    /**
     * May return a {@link LockingInhibitor} or just empty {@link Optional}, if for example disabled.
     */
    Optional<LockingInhibitor> newInstance(RepositorySystemSession session);
}
