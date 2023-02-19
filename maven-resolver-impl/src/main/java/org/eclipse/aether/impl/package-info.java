// CHECKSTYLE_OFF: RegexpHeader
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
/**
 * The provisional interfaces defining the various sub components that implement the repository system. Aether Core
 * provides stock implementations for most of these components but not all. To obtain a complete/runnable repository
 * system, the application needs to provide implementations of the following component contracts:
 * {@link org.eclipse.aether.impl.ArtifactDescriptorReader}, {@link org.eclipse.aether.impl.VersionResolver},
 * {@link org.eclipse.aether.impl.VersionRangeResolver} and potentially
 * {@link org.eclipse.aether.impl.MetadataGeneratorFactory}. Said components basically define the file format of the
 * metadata that is used to reason about an artifact's dependencies and available versions.
 */
package org.eclipse.aether.impl;
