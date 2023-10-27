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
package org.eclipse.aether.util.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class DefaultProxySelectorTest {

    private boolean isNonProxyHost(String host, String nonProxyHosts) {
        return new DefaultProxySelector.NonProxyHosts(nonProxyHosts).isNonProxyHost(host);
    }

    @Test
    void testIsNonProxyHost_Blank() {
        assertFalse(isNonProxyHost("www.eclipse.org", null));
        assertFalse(isNonProxyHost("www.eclipse.org", ""));
    }

    @Test
    void testIsNonProxyHost_Wildcard() {
        assertTrue(isNonProxyHost("www.eclipse.org", "*"));
        assertTrue(isNonProxyHost("www.eclipse.org", "*.org"));
        assertFalse(isNonProxyHost("www.eclipse.org", "*.com"));
        assertTrue(isNonProxyHost("www.eclipse.org", "www.*"));
        assertTrue(isNonProxyHost("www.eclipse.org", "www.*.org"));
    }

    @Test
    void testIsNonProxyHost_Multiple() {
        assertTrue(isNonProxyHost("eclipse.org", "eclipse.org|host2"));
        assertTrue(isNonProxyHost("eclipse.org", "host1|eclipse.org"));
        assertTrue(isNonProxyHost("eclipse.org", "host1|eclipse.org|host2"));
    }

    @Test
    void testIsNonProxyHost_Misc() {
        assertFalse(isNonProxyHost("www.eclipse.org", "www.eclipse.com"));
        assertFalse(isNonProxyHost("www.eclipse.org", "eclipse.org"));
    }

    @Test
    void testIsNonProxyHost_CaseInsensitivity() {
        assertTrue(isNonProxyHost("www.eclipse.org", "www.ECLIPSE.org"));
        assertTrue(isNonProxyHost("www.ECLIPSE.org", "www.eclipse.org"));
    }
}
