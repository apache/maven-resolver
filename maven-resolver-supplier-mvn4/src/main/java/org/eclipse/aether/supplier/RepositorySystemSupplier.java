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

import org.apache.maven.repository.internal.*;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.internal.impl.*;

/**
 * A simple memorizing {@link Supplier} of {@link org.eclipse.aether.RepositorySystem} instance, that on first call
 * supplies lazily constructed instance, and on each subsequent call same instance. Hence, this instance should be
 * thrown away immediately once repository system was created and there is no need for more instances. If new
 * repository system instance needed, new instance of this class must be created. For proper shut down of returned
 * repository system instance(s) use {@link RepositorySystem#shutdown()} method on supplied instance(s).
 * <p>
 * Since Resolver 2.0 this class offers access to various components via public getters, and allows even partial object
 * graph construction.
 * <p>
 * Extend this class {@code createXXX()} methods and override to customize, if needed. The contract of this class makes
 * sure that these (potentially overridden) methods are invoked only once, and instance created by those methods are
 * memorized and kept as long as supplier instance is kept open.
 * <p>
 * This class is not thread safe and must be used from one thread only, while the constructed {@link RepositorySystem}
 * is thread safe.
 * <p>
 * Important: Given the instance of supplier memorizes the supplier {@link RepositorySystem} instance it supplies,
 * their lifecycle is shared as well: once supplied repository system is shut-down, this instance becomes closed as
 * well. Any subsequent {@code getXXX} method invocation attempt will fail with {@link IllegalStateException}.
 *
 * @since 1.9.15
 */
public class RepositorySystemSupplier extends MavenRepositorySystemSupplier {}
