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

import java.util.Collection;
import java.util.Collections;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * Static {@link NameMapper}, always assigns one same name, effectively becoming equivalent to "static" sync context:
 * always maps ANY input to same name.
 */
public class StaticNameMapper implements NameMapper {
    @Override
    public boolean isFileSystemFriendly() {
        return true;
    }

    @Override
    public Collection<String> nameLocks(
            final RepositorySystemSession session,
            final Collection<? extends Artifact> artifacts,
            final Collection<? extends Metadata> metadatas) {
        if (artifacts != null && !artifacts.isEmpty()) {
            return Collections.singletonList( "static-artifact" );
        } else if (metadatas != null && !metadatas.isEmpty()) {
            return Collections.singletonList("static-metadata");

        } else {
            return Collections.emptyList();
        }
    }
}
