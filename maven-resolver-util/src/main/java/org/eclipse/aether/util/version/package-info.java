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
 * Ready-to-use version scheme for parsing/comparing versions and utility classes.
 * <p>
 * Contains the "generic" scheme {@link org.eclipse.aether.util.version.GenericVersionScheme}
 * that serves the purpose of "factory" (and/or parser) for all corresponding elements (all those are package private).
 * <p>
 * On the other hand, the {@link org.eclipse.aether.util.version.UnionVersionRange} is universal implementation of
 * "unions" of various {@link org.eclipse.aether.version.VersionRange} instances.
 */
package org.eclipse.aether.util.version;
