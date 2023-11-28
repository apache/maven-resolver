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
package org.apache.maven.resolver.examples.supplier;

import java.util.Map;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.transport.jdk.JdkTransporterFactory;
import org.eclipse.aether.transport.jetty.JettyTransporterFactory;

/**
 * A factory for repository system instances that employs Maven Artifact Resolver's provided supplier.
 */
public class SupplierRepositorySystemFactory {
    public static RepositorySystem newRepositorySystem() {
        return new RepositorySystemSupplier() {
            @Override
            protected Map<String, TransporterFactory> getTransporterFactories() {
                Map<String, TransporterFactory> result = super.getTransporterFactories();
                result.put(JdkTransporterFactory.NAME, new JdkTransporterFactory());
                result.put(JettyTransporterFactory.NAME, new JettyTransporterFactory());
                return result;
            }
        }.get();
    }
}
