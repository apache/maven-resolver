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
 * A stateless component of the repository system. The primary purpose of this interface is to provide a convenient
 * means to programmatically wire the several components of the repository system together when it is used outside of an
 * IoC container.
 *
 * @deprecated Use some out-of-the-box DI implementation instead.
 */
@Deprecated
public interface Service {

    /**
     * Provides the opportunity to initialize this service and to acquire other services for its operation from the
     * locator. A service must not save the reference to the provided service locator.
     *
     * @param locator The service locator, must not be {@code null}.
     */
    void initService(ServiceLocator locator);
}
