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
package org.eclipse.aether.util.concurrency;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A factory to create worker threads with a given name prefix.
 */
public final class WorkerThreadFactory implements ThreadFactory {

    private final ThreadFactory factory;

    private final String namePrefix;

    private final AtomicInteger threadIndex;

    private static final AtomicInteger POOL_INDEX = new AtomicInteger();

    /**
     * Creates a new thread factory whose threads will have names using the specified prefix.
     *
     * @param namePrefix The prefix for the thread names, may be {@code null} or empty to derive the prefix from the
     *            caller's simple class name.
     */
    public WorkerThreadFactory(String namePrefix) {
        this.factory = Executors.defaultThreadFactory();
        this.namePrefix =
                ((namePrefix != null && namePrefix.length() > 0) ? namePrefix : getCallerSimpleClassName() + '-')
                        + POOL_INDEX.getAndIncrement()
                        + '-';
        threadIndex = new AtomicInteger();
    }

    private static String getCallerSimpleClassName() {
        StackTraceElement[] stack = new Exception().getStackTrace();
        if (stack == null || stack.length <= 2) {
            return "Worker-";
        }
        String name = stack[2].getClassName();
        name = name.substring(name.lastIndexOf('.') + 1);
        return name;
    }

    public Thread newThread(Runnable r) {
        Thread thread = factory.newThread(r);
        thread.setName(namePrefix + threadIndex.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
