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
package org.eclipse.aether.named.support;

/**
 * Exception thrown when lock upgrade attempted that we do not support. This exception when used within {@link Retry}
 * helper should never be reattempted, hence is marked with {@link Retry.DoNotRetry} marker.
 *
 * @since 1.9.13
 */
public final class LockUpgradeNotSupportedException extends RuntimeException implements Retry.DoNotRetry {
    /**
     * Constructor for case, when current thread attempts lock upgrade on given lock instance.
     */
    public LockUpgradeNotSupportedException(NamedLockSupport namedLock) {
        super("Thread " + Thread.currentThread().getName()
                + " already holds shared lock for '" + namedLock.name()
                + "', but asks for exclusive lock; lock upgrade not supported");
    }
}
