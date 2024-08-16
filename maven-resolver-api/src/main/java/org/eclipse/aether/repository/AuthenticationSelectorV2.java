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
package org.eclipse.aether.repository;

import java.net.URI;

/**
 * Selects authentication for a given remote repository.
 *
 * @see org.eclipse.aether.RepositorySystemSession#getAuthenticationSelector()
 */
public interface AuthenticationSelectorV2 {

    /**
     * Selects authentication for the specified URI, scheme and realm.
     *
     * @param uri the URI of the request for which to select the authentication, must not be {@code null}.
     * @param scheme the authentication scheme being requested (may be {@code null}), for HTTP  one of the names outlined at <a href="https://www.iana.org/assignments/http-authschemes/http-authschemes.xhtml">Hypertext Transfer Protocol (HTTP) Authentication Scheme Registry</a>
     * @param realm the authentication realm being requested (may be {@code null})
     * 
     * @return The selected authentication or {@code null} if none.
     */
    Authentication getAuthentication(URI uri, String scheme, String realm);
}
