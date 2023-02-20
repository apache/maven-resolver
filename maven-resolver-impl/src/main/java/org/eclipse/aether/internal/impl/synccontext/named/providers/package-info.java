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
 * As end-user "mappers" are actually configurations, constructed from several NameMapper implementations, this
 * package have providers exposing name mappers constructed by
 * {@link org.eclipse.aether.internal.impl.synccontext.named.NameMappers} helper class. These classes are used
 * when Guice/Sisu is used, as they assume {@link javax.inject.Provider} class to be present.
 */
package org.eclipse.aether.internal.impl.synccontext.named.providers;
