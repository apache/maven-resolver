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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.ISemaphore;

/**
 * Support class for providers of {@link ISemaphore} instances.
 */
public abstract class HazelcastSemaphoreProvider
{
    /**
     * Name prefix recommended using for simpler configuration of Hazelcast.
     */
    protected static final String NAME_PREFIX = "mvn:resolver:";

    /**
     * Invoked when new instance of semaphore needed for given name. must not return {@code null}.
     */
    public abstract ISemaphore acquireSemaphore( HazelcastInstance hazelcastInstance, String name );

    /**
     * Invoked when passed in semaphore associated with passed in name is not to be used anymore.
     */
    public abstract void releaseSemaphore( HazelcastInstance hazelcastInstance, String name, ISemaphore semaphore );
}
