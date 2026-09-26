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
package org.eclipse.aether.crossmodule;

import org.eclipse.aether.ConfigurationProperties;

/** Configuration key whose type is declared in a sibling reactor module. */
public final class CrossModuleConfigurationKeys {

    /**
     * A configuration value backed by a type from another module.
     *
     * @configurationSource {@link System#getProperty(String,String)}
     * @configurationType {@link ConfigurationProperties.HttpVersion}
     * @configurationDefaultValue DEFAULT
     */
    public static final String HTTP_VERSION = "sample.crossModule";

    private CrossModuleConfigurationKeys() {}
}
