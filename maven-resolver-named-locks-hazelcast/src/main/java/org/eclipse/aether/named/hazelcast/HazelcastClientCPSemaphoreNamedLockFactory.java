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

import com.hazelcast.client.HazelcastClient;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Provider of {@link HazelcastSemaphoreNamedLockFactory} using Hazelcast Client and {@link
 * com.hazelcast.core.HazelcastInstance#getCPSubsystem()} method to get CP semaphore. For this to work, the Hazelcast
 * cluster the client is connecting to must be CP enabled cluster.
 */
@Singleton
@Named( HazelcastClientCPSemaphoreNamedLockFactory.NAME )
public class HazelcastClientCPSemaphoreNamedLockFactory
    extends HazelcastSemaphoreNamedLockFactory
{
  public static final String NAME = "semaphore-hazelcast-client";

  @Inject
  public HazelcastClientCPSemaphoreNamedLockFactory()
  {
    super(
        HazelcastClient.newHazelcastClient(),
        ( hazelcastInstance, name ) -> hazelcastInstance.getCPSubsystem().getSemaphore( NAME_PREFIX + name ),
        false,
        true
    );
  }
}
