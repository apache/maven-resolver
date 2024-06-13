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
package org.eclipse.aether.named.ipc;

import org.junit.jupiter.api.BeforeAll;

public class IpcNamedLockFactoryIT extends NamedLockFactoryTestSupport {
    @BeforeAll
    static void createNamedLockFactory() {
        System.setProperty(IpcServer.SYSTEM_PROP_DEBUG, Boolean.TRUE.toString());
        System.setProperty(IpcServer.SYSTEM_PROP_NO_NATIVE, Boolean.TRUE.toString());
        namedLockFactory = new IpcNamedLockFactory() {
            @Override
            protected void doShutdown() {
                client.stopServer();
                super.doShutdown();
            }
        };
        ;
    }
}
