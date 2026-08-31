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

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.aether.named.NamedLockKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * UT for the lock key digest computation of {@link IpcNamedLockFactory}: distinct key collections must have
 * distinct lock identities.
 */
public class IpcNamedLockFactoryTest {

    @Test
    void sameKeysSameDigest() {
        assertEquals(
                IpcNamedLockFactory.digestKeys(Arrays.asList(NamedLockKey.of("foo"), NamedLockKey.of("bar"))),
                IpcNamedLockFactory.digestKeys(Arrays.asList(NamedLockKey.of("foo"), NamedLockKey.of("bar"))));
    }

    @Test
    void boundaryShiftedKeysHaveDistinctDigests() {
        // all three concatenate to "foobar" but are different key collections
        assertNotEquals(
                IpcNamedLockFactory.digestKeys(Arrays.asList(NamedLockKey.of("foo"), NamedLockKey.of("bar"))),
                IpcNamedLockFactory.digestKeys(Arrays.asList(NamedLockKey.of("foob"), NamedLockKey.of("ar"))));
        assertNotEquals(
                IpcNamedLockFactory.digestKeys(Arrays.asList(NamedLockKey.of("foo"), NamedLockKey.of("bar"))),
                IpcNamedLockFactory.digestKeys(Collections.singletonList(NamedLockKey.of("foobar"))));
    }

    @Test
    void keyCountIsPartOfIdentity() {
        assertNotEquals(
                IpcNamedLockFactory.digestKeys(Collections.singletonList(NamedLockKey.of(""))),
                IpcNamedLockFactory.digestKeys(Arrays.asList(NamedLockKey.of(""), NamedLockKey.of(""))));
    }

    @Test
    void lengthPrefixLookalikeNamesDoNotCollide() {
        // a name that itself starts with digits and a colon must not be confusable with the length prefix
        assertNotEquals(
                IpcNamedLockFactory.digestKeys(Collections.singletonList(NamedLockKey.of("3:abc"))),
                IpcNamedLockFactory.digestKeys(Collections.singletonList(NamedLockKey.of("abc"))));
    }
}
