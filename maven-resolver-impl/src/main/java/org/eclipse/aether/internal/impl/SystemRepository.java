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

import org.eclipse.aether.repository.ArtifactRepository;

/**
 * A repository representing "system" scope, basically this repository has no artifacts. By special meaning of this
 * repository, it is not part of the public API nor is publicly visible, is merely here to help assign artifact
 * origin to artifacts in case of resolution errors.
 *
 * @since 2.0.0
 */
final class SystemRepository implements ArtifactRepository {
    public static final SystemRepository INSTANCE = new SystemRepository();

    private SystemRepository() {}

    public String getContentType() {
        return "system";
    }

    public String getId() {
        return "system";
    }

    @Override
    public String toString() {
        return getId();
    }
}
