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
package org.eclipse.aether.internal.impl.synccontext.named;

import org.eclipse.aether.RepositorySystemSession;

/**
 * Factory for {@link NamedLockFactoryAdapter}.
 *
 * @since 1.9.1
 */
public interface NamedLockFactoryAdapterFactory {
    /**
     * Creates or returns pre-created {@link NamedLockFactoryAdapter}, never {@code null}.
     * <p>
     * It is left at the discretion of the implementation what happens on this method call: it may create always new
     * instances, or return the same instance. One thing is required for the implementation: to shut down any name
     * lock factory it used during any invocation of this method.
     */
    NamedLockFactoryAdapter getAdapter(RepositorySystemSession session);
}
