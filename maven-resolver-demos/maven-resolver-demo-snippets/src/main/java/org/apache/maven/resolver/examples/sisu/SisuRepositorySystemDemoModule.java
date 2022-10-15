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
package org.apache.maven.resolver.examples.sisu;

import com.google.inject.Binder;
import com.google.inject.Module;
import javax.inject.Inject;
import org.eclipse.sisu.inject.MutableBeanLocator;
import org.eclipse.sisu.wire.ParameterKeys;

/**
 * Sisu module for demo snippets.
 */
public class SisuRepositorySystemDemoModule implements Module {
    @Override
    public void configure(final Binder binder) {
        // NOTE: Maven 3.8.1 used in demo has Sisu Index for ALL components (older Maven does NOT have!)
        binder.bind(ParameterKeys.PROPERTIES).toInstance(System.getProperties());
        binder.bind(ShutdownThread.class).asEagerSingleton();
    }

    static final class ShutdownThread extends Thread {
        private final MutableBeanLocator locator;

        @Inject
        ShutdownThread(final MutableBeanLocator locator) {
            this.locator = locator;
            Runtime.getRuntime().addShutdownHook(this);
        }

        @Override
        public void run() {
            locator.clear();
        }
    }
}
