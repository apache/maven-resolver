package org.eclipse.aether.named.hazelcast;

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

import com.hazelcast.config.Config;
import com.hazelcast.config.cp.CPSubsystemConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hazelcast Client connects to remote server (or servers, in case of cluster). This class is a helper to
 * help create and validate remote Hazelcast environment, that Hazelcast Client will connect to.
 */
public final class HazelcastClientUtils {

    private static final int CP_CLUSTER_NODES = 3;

    private List<HazelcastInstance> servers;

    /**
     * Creates a Hazelcast server instance, that client may connect to.
     */
    public HazelcastClientUtils createSingleServer() {
        servers = Collections.singletonList(Hazelcast.newHazelcastInstance());
        return this;
    }

    /**
     * Creates a Hazelcast CP cluster, that client may connect to. When this method returns, cluster is not only
     * created but it is properly formed as well.
     */
    public HazelcastClientUtils createCpCluster() {
        ArrayList<HazelcastInstance> instances = new ArrayList<>(CP_CLUSTER_NODES);
        for (int i = 0; i < CP_CLUSTER_NODES; i++) {
            HazelcastInstance instance = Hazelcast.newHazelcastInstance(
                loadCPNodeConfig().setInstanceName("node" + i)
            );
            instances.add(instance);
        }
        servers = instances;

        // make sure HZ CP Cluster is ready
        for (HazelcastInstance instance : servers) {
            // this call will block until CP cluster if formed
            // important thing here is that this blocking does not happen during timeout surrounded test
            // hence, once this method returns, the CP cluster is "ready for use" without any delay.
            instance.getCPSubsystem().getAtomicLong(instance.getName());
        }
        return this;
    }

    /**
     * Shuts down the created server(s).
     */
    public void cleanup() {
        if (servers != null) {
            servers.forEach(HazelcastInstance::shutdown);
        }
    }

    private Config loadCPNodeConfig() {
        // "cluster" for CP needs some config tweak from the test/resources/hazelcast.xml
        Config config = Config.load().setCPSubsystemConfig(new CPSubsystemConfig().setCPMemberCount(3));
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);
        return config;
    }
}
