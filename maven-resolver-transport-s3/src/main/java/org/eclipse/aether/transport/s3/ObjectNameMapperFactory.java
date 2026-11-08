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
package org.eclipse.aether.transport.s3;

import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Factory for {@link ObjectNameMapper}.
 *
 * @since 2.0.26
 */
public interface ObjectNameMapperFactory {
    /**
     * Creates an {@link ObjectNameMapper} that maps artifact layout paths onto {@link ObjectName}s for the given
     * session and repository. Should never return {@code null}.
     */
    ObjectNameMapper create(
            RepositorySystemSession session, RemoteRepository repository, Map<String, String> extraHeaders);
}
