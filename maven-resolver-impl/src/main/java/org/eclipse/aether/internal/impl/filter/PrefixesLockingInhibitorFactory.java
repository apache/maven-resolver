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
package org.eclipse.aether.internal.impl.filter;

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.spi.locking.LockingInhibitor;
import org.eclipse.aether.spi.locking.LockingInhibitorFactory;

/**
 * Locking inhibitor for prefix files. They are perfect candidates, as they are created on remote, and locally are
 * only cached and read, and they do not clash on local file system (local repo) as they are stored in a file
 * that has origin repository factored in, as they are metadata.
 *
 * @since 2.0.14
 */
@Singleton
@Named(PrefixesLockingInhibitorFactory.NAME)
public class PrefixesLockingInhibitorFactory implements LockingInhibitorFactory, LockingInhibitor {
    public static final String NAME = PrefixesRemoteRepositoryFilterSource.NAME;

    @Override
    public Optional<LockingInhibitor> newInstance(RepositorySystemSession session) {
        return Optional.of(this);
    }

    @Override
    public Optional<Predicate<Metadata>> inhibitMetadataLocking() {
        return Optional.of(m -> "".equals(m.getGroupId())
                && "".equals(m.getArtifactId())
                && "".equals(m.getVersion())
                && PrefixesRemoteRepositoryFilterSource.PREFIX_FILE_TYPE.equals(m.getType()));
    }
}
