// CHECKSTYLE_OFF: RegexpHeader
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
/**
 * A lightweight service locator infrastructure to help components acquire dependent components. The implementation of
 * the repository system is decomposed into many sub components that interact with each other via interfaces, allowing
 * an application to customize the system by swapping in different implementation classes for these interfaces. The
 * service locator defined by this package is one means for components to get hold of the proper implementation for its
 * dependencies. While not the most popular approach to component wiring, this service locator enables applications
 * that do not wish to pull in more sophisticated solutions like dependency injection containers to have a small
 * footprint. Therefore, all components should implement {@link org.eclipse.aether.spi.locator.Service} to support this
 * goal.
 *
 * @deprecated Use some out-of-the-box DI implementation instead.
 */
package org.eclipse.aether.spi.locator;
