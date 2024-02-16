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
import com.hazelcast.core.HazelcastInstance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * {@link HazelcastSemaphoreNamedLockFactory} using {@link DirectHazelcastSemaphoreProvider} and Hazelcast client. The
 * client must be configured to connect to some existing cluster (w/ proper configuration applied).
 */
@Singleton
@Named(HazelcastClientCPSemaphoreNamedLockFactory.NAME)
public class HazelcastClientCPSemaphoreNamedLockFactory extends HazelcastSemaphoreNamedLockFactory {
    public static final String NAME = "semaphore-hazelcast-client";

    /**
     * The default constructor: creates own instance of Hazelcast using standard Hazelcast configuration discovery.
     */
    @Inject
    public HazelcastClientCPSemaphoreNamedLockFactory() {
        this(HazelcastClient.newHazelcastClient(), true);
    }

    /**
     * Constructor for customization.
     */
    public HazelcastClientCPSemaphoreNamedLockFactory(HazelcastInstance hazelcastInstance, boolean manageHazelcast) {
        super(hazelcastInstance, manageHazelcast, new DirectHazelcastSemaphoreProvider());
    }
}
