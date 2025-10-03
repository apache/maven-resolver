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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

/**
 * {@link HazelcastSemaphoreNamedLockFactory} using {@link DirectHazelcastSemaphoreProvider} full Hazelcast member.
 *
 * @deprecated Hazelcast support will be dropped.
 */
@Deprecated
@Singleton
@Named(HazelcastCPSemaphoreNamedLockFactory.NAME)
public class HazelcastCPSemaphoreNamedLockFactory extends HazelcastSemaphoreNamedLockFactory {
    public static final String NAME = "semaphore-hazelcast";

    /**
     * The default constructor: creates own instance of Hazelcast using standard Hazelcast configuration discovery.
     */
    @Inject
    public HazelcastCPSemaphoreNamedLockFactory() {
        this(Hazelcast.newHazelcastInstance(), true);
    }

    /**
     * Constructor for customization.
     */
    public HazelcastCPSemaphoreNamedLockFactory(HazelcastInstance hazelcastInstance, boolean manageHazelcast) {
        super(hazelcastInstance, manageHazelcast, new DirectHazelcastSemaphoreProvider());
    }
}
