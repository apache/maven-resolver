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
package org.eclipse.aether.supplier;

import java.util.function.Supplier;

import org.apache.maven.impl.resolver.MavenSessionBuilderSupplier;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.RepositorySystemSession.SessionBuilder;

/**
 * A simple {@link Supplier} of {@link SessionBuilder} instances, that on each call supplies newly
 * constructed instance. To create session out of builder, use {@link SessionBuilder#build()}. For proper closing
 * of sessions, use {@link CloseableSession#close()} method on built instance(s).
 * <p>
 * Extend this class and override methods to customize, if needed.
 *
 * @since 2.0.0
 */
public class SessionBuilderSupplier extends MavenSessionBuilderSupplier {
    public SessionBuilderSupplier(RepositorySystem repositorySystem) {
        super(repositorySystem);
    }
}
