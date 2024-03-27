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

/**
 * Label for "build path", like "compile", "runtime", etc.
 *
 * @since 2.0.0
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface BuildPath {
    /**
     * The label.
     */
    String getId();

    /**
     * A flag denoting that this build path "reverses" the expected order of project paths.
     * <p>
     * For example: "compile" step expects "main" then "test" order (compile them in this order).
     * On the other hand, "runtime" expects "test" to have run first. In this sense, "runtime" is reverse.
     * <p>
     * If {@code false}, then {@link ProjectPath#order()} is used to sort project paths, otherwise the
     * {@link ProjectPath#reverseOrder()} is used to sort them.
     */
    boolean isReverse();

    /**
     * Returns the "order" of this path, usable to sort against other instances.
     * Expected natural order is "compile", "runtime"... (basically like the processing order).
     */
    int order();
}
