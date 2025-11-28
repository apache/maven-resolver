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

import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryKeyFunction;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RepositoryIdHelperTest {
    private final RemoteRepository central = new RemoteRepository.Builder(
                    "central", "default", "https://repo.maven.apache.org/maven2/")
            .setSnapshotPolicy(new RepositoryPolicy(false, null, null))
            .build();
    private final RemoteRepository central_legacy = new RemoteRepository.Builder(
                    "central", "default", "https://repo1.maven.org/maven2/")
            .setSnapshotPolicy(new RepositoryPolicy(false, null, null))
            .build();
    private final RemoteRepository central_trivial = new RemoteRepository.Builder(
            "central", "default", "https://repo1.maven.org/maven2/")
            .build();
    private final RemoteRepository central_mirror = new RemoteRepository.Builder(
            "my-mirror", "default", "https://mymrm.com/maven/")
            .setSnapshotPolicy(new RepositoryPolicy(false, null, null))
            .setMirroredRepositories(Collections.singletonList(central))
            .build();
    private final RemoteRepository asf_snapshots = new RemoteRepository.Builder(
                    "apache-snapshots", "default", "https://repository.apache.org/content/repositories/snapshots/")
            .setReleasePolicy(new RepositoryPolicy(false, null, null))
            .build();
    private final RemoteRepository file_unfriendly = new RemoteRepository.Builder(
            "apache/snapshots", "default", "https://repository.apache.org/content/repositories/snapshots/")
            .setReleasePolicy(new RepositoryPolicy(false, null, null))
            .build();

    @Test
    void simple() {
        RepositoryKeyFunction func = RepositoryIdHelper.getRepositoryKeyFunction(RepositoryIdHelper.RepositoryKeyType.SIMPLE.name());
        assertEquals("central", func.apply(central, null));
        assertEquals("central", func.apply(central_legacy, null));
        assertEquals("central", func.apply(central_trivial, null));
        assertEquals("my-mirror", func.apply(central_mirror, null));
        assertEquals("apache-snapshots", func.apply(asf_snapshots, null));
        assertEquals("apache-SLASH-snapshots", func.apply(file_unfriendly, null));
    }

    @Test
    void nid() {
        RepositoryKeyFunction func = RepositoryIdHelper.getRepositoryKeyFunction(RepositoryIdHelper.RepositoryKeyType.NID.name());
        assertEquals("central", func.apply(central, null));
        assertEquals("central", func.apply(central_legacy, null));
        assertEquals("central", func.apply(central_trivial, null));
        assertEquals("my-mirror", func.apply(central_mirror, null));
        assertEquals("apache-snapshots", func.apply(asf_snapshots, null));
        assertEquals("apache-SLASH-snapshots", func.apply(file_unfriendly, null));
    }

    @Test
    void nidHurl() {
        RepositoryKeyFunction func = RepositoryIdHelper.getRepositoryKeyFunction(RepositoryIdHelper.RepositoryKeyType.NID_HURL.name());
        assertEquals("central-0aeeb43004cebeccad6fdf0fec27084167d5880a", func.apply(central, null));
        assertEquals("central-a27bb55260d64d6035671716555d10644054c89d", func.apply(central_legacy, null));
        assertEquals("central-a27bb55260d64d6035671716555d10644054c89d", func.apply(central_trivial, null));
        assertEquals("my-mirror-eb106d0adc4a56b55067f069a2fed5526fd6cb18", func.apply(central_mirror, null));
        assertEquals("apache-snapshots-5c4f89479e3c71fb3c2fbc6213fb00f6371fbb96", func.apply(asf_snapshots, null));
        assertEquals("apache-SLASH-snapshots-5c4f89479e3c71fb3c2fbc6213fb00f6371fbb96", func.apply(file_unfriendly, null));
    }

    @Test
    void ngurk() {
        RepositoryKeyFunction func = RepositoryIdHelper.getRepositoryKeyFunction(RepositoryIdHelper.RepositoryKeyType.NGURK.name());
        assertEquals("central-ff5deec948d038ceb880e13e9f61455903b0d0a6", func.apply(central, null));
        assertEquals("central-ffb5c2a34e47c429571fc29752730e9ce6e44d79", func.apply(central_legacy, null));
        assertEquals("central-acc6c84ca8674036eda6708502b5f02fb09a9731", func.apply(central_trivial, null));
        assertEquals("my-mirror-256631324003f5718aca1e80db8377c7f9ecd852", func.apply(central_mirror, null));
        assertEquals("apache-snapshots-62375dea6c3c8bebdbae5cca79a4f5ad2eaebf34", func.apply(asf_snapshots, null));
        assertEquals("apache-SLASH-snapshots-2e126ec79795c077a3c42dc536fa28c13c3bdb0d", func.apply(file_unfriendly, null));
    }
}
