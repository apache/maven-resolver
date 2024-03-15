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
package org.eclipse.aether.impl.scope;

import java.util.Set;

/**
 * Build scope is certain combination of {@link ProjectPath} and {@link BuildPath}.
 *
 * @since 2.0.0
 */
public interface BuildScope {
    /**
     * The label.
     */
    String getId();

    /**
     * The project paths this scope belongs to.
     */
    Set<ProjectPath> getProjectPaths();

    /**
     * The build paths this scope belongs to.
     */
    Set<BuildPath> getBuildPaths();

    /**
     * Returns the "order" of this scope, usable to sort against other instances.
     * Expected natural order is "main-compile", "test-compile"... (basically like the processing order).
     * <p>
     * Note: this order is unrelated to {@link ProjectPath#order()} and {@link BuildPath#order()} and
     * should be used only to sort build scope instances.
     */
    int order();
}
