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
 * Label for "project path", like "main", "test", but could be "it", etc.
 *
 * @since 2.0.0
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface ProjectPath {
    /**
     * The label.
     */
    String getId();

    /**
     * Returns the "order" of this path, usable to sort against other instances.
     * Expected natural order is "main", "test"... (basically like the processing order).
     */
    int order();

    /**
     * Returns the "reverse order" of this path, usable to sort against other instances.
     * Expected natural order is "test", "main"... (basically like the processing order).
     */
    int reverseOrder();
}
