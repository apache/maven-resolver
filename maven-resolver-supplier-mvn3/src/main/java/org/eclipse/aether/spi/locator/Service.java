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
package org.eclipse.aether.spi.locator;

/**
 * Binary-compatibility shim for Maven 3 code that still links against the Resolver 1.x locator-aware service API.
 * Resolver 2.x does not provide a service locator: this interface is never implemented or looked up by Resolver
 * itself, and it is not available on the class path of applications that do not use this module.
 *
 * @since 2.0.0
 * @deprecated Use JSR-330 dependency injection (Eclipse Sisu, Guice) or
 * {@link org.eclipse.aether.supplier.RepositorySystemSupplier} to obtain a {@code RepositorySystem}.
 */
@Deprecated
public interface Service {}
