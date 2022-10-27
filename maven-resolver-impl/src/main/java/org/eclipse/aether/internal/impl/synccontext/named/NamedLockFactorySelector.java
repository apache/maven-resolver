package org.eclipse.aether.internal.impl.synccontext.named;

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

import org.eclipse.aether.named.NamedLockFactory;

/**
 * Selector for {@link NamedLockFactory} and {@link NameMapper} that selects and exposes selected ones. Essentially
 * all the named locks configuration is here. Implementations may use different strategies to perform selection.
 *
 * @since 1.7.3
 */
public interface NamedLockFactorySelector
{
    /**
     * Returns the selected {@link NamedLockFactory}, never {@code null}.
     */
    NamedLockFactory getSelectedNamedLockFactory();

    /**
     * Returns the selected {@link NameMapper}, never {@code null}.
     */
    NameMapper getSelectedNameMapper();
}