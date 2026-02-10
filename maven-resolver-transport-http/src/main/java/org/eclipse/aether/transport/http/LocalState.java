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
package org.eclipse.aether.transport.http;

import java.io.Closeable;

import org.apache.http.conn.HttpClientConnectionManager;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transport.http.GlobalState.CompoundKey;

/**
 * Container for HTTP-related state that can be shared across invocations of the transporter to optimize the
 * communication with server.
 */
final class LocalState implements Closeable {
    private final GlobalState global;

    private final HttpClientConnectionManager connMgr;

    private final CompoundKey userTokenKey;

    private volatile Object userToken;

    private final CompoundKey expectContinueKey;

    private volatile Boolean expectContinue;

    private volatile Boolean webDav;

    LocalState(RepositorySystemSession session, RemoteRepository repo, ConnMgrConfig connMgrConfig) {
        global = GlobalState.get(session);
        userToken = this;
        if (global == null) {
            connMgr = GlobalState.newConnectionManager(connMgrConfig);
            userTokenKey = null;
            expectContinueKey = null;
        } else {
            connMgr = global.getConnectionManager(connMgrConfig);
            userTokenKey = new CompoundKey(repo.getId(), repo.getUrl(), repo.getAuthentication(), repo.getProxy());
            expectContinueKey = new CompoundKey(repo.getUrl(), repo.getProxy());
        }
    }

    public HttpClientConnectionManager getConnectionManager() {
        return connMgr;
    }

    public Object getUserToken() {
        if (userToken == this) {
            userToken = (global != null) ? global.getUserToken(userTokenKey) : null;
        }
        return userToken;
    }

    public void setUserToken(Object userToken) {
        this.userToken = userToken;
        if (global != null) {
            global.setUserToken(userTokenKey, userToken);
        }
    }

    public boolean isExpectContinue() {
        if (expectContinue == null) {
            expectContinue =
                    !Boolean.FALSE.equals((global != null) ? global.getExpectContinue(expectContinueKey) : null);
        }
        return expectContinue;
    }

    public void setExpectContinue(boolean enabled) {
        expectContinue = enabled;
        if (global != null) {
            global.setExpectContinue(expectContinueKey, enabled);
        }
    }

    public Boolean getWebDav() {
        return webDav;
    }

    public void setWebDav(boolean webDav) {
        this.webDav = webDav;
    }

    @Override
    public void close() {
        if (global == null) {
            connMgr.shutdown();
        }
    }
}
