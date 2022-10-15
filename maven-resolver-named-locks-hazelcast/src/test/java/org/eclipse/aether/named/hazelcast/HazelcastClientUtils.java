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
package org.eclipse.aether.named.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Hazelcast test utilities.
 */
public final class HazelcastClientUtils {
    private final List<HazelcastInstance> servers = new ArrayList<>();

    /**
     * Creates similar but still randomized name.
     */
    public String clusterName(Class<?> klazz) {
        return String.format("%s-%s", klazz.getSimpleName(), UUID.randomUUID());
    }

    /**
     * Creates single Hazelcast client instance.
     */
    public synchronized HazelcastInstance createClient(String clusterName) {
        ClientConfig config = ClientConfig.load();
        config.setClusterName(clusterName);
        return HazelcastClient.newHazelcastClient(config);
    }

    /**
     * Creates single Hazelcast member instance.
     */
    public synchronized HazelcastInstance createMember(String clusterName) {
        return createMembers(1, clusterName).get(0);
    }

    /**
     * Creates given count of Hazelcast member instances.
     */
    public synchronized List<HazelcastInstance> createMembers(int memberCount, String clusterName) {
        Config config = Config.load();
        config.setClusterName(clusterName);
        ArrayList<HazelcastInstance> result = new ArrayList<>(memberCount);
        IntStream.range(0, memberCount).forEach(i -> {
            config.setInstanceName("node-" + i);
            HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);
            result.add(instance);
            servers.add(instance);
        });
        return result;
    }

    /**
     * Shuts down the created instances.
     */
    public synchronized void cleanup() {
        servers.forEach(HazelcastInstance::shutdown);
        servers.clear();
    }
}
