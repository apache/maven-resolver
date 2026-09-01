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
package org.eclipse.aether.transport.apache5;

import org.apache.hc.client5.http.protocol.HttpClientContext;

/**
 * HTTP context that shares certain attributes among requests to optimize the communication with the server.
 *
 * <p>Apache HttpClient 5 stores the per-connection user token (see {@link #getUserToken()}/{@link
 * #setUserToken(Object)}) in a private typed field, not via the generic {@link #getAttribute(String)}/{@link
 * #setAttribute(String, Object)} map, so seeding and capturing it must go through the typed accessors: this class
 * seeds the token persisted in {@link LocalState} into the new context here, and the caller reads it back with
 * {@link #getUserToken()} after the request completes to persist it again.
 *
 * @see <a href="http://hc.apache.org/httpcomponents-client-ga/tutorial/html/advanced.html#stateful_conn">Stateful HTTP
 *      connections</a>
 */
final class SharingHttpContext extends HttpClientContext {

    SharingHttpContext(LocalState state) {
        Object token = state.getUserToken();
        if (token != null) {
            setUserToken(token);
        }
    }
}
