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
 * The Transform API of resolver: a very powerful API, so take care.
 * <p>
 * The deprecated {@link org.eclipse.aether.transform.FileTransformer} is able to alter install/deploy artifact
 * coordinates and/or its content. Still, the API is OOM prone, and is deprecated.
 * <p>
 * The new {@link org.eclipse.aether.transform.ArtifactTransformer} serves similar purpose, but adds several
 * benefits: it is able to distinguish install/deploy operation, is able to inhibit (prevent) install or deploy,
 * is able to completely replace artifact and/or its content as well.
 * <p>
 * Note: if {@link org.eclipse.aether.transform.FileTransformer} is present, it overrides the
 * {@link org.eclipse.aether.transform.ArtifactTransformer}, so please use either this or that. In future, the
 * deprecated transformer will be removed.
 */
package org.eclipse.aether.transform;

