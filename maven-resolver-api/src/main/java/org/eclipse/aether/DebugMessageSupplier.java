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
package org.eclipse.aether;

/**
 * Supplies a debug message.
 * Usually used together with a {@link Throwable} to provide additional information about the error.
 *
 * @since 2.0.26
 */
@FunctionalInterface
public interface DebugMessageSupplier {
    /**
     * Returns a debug message.
     * If implemented by a {@link Throwable}, this message should be used in addition to {@link Throwable#getMessage()} to provide more context.
     * However, this should only be logged in debug mode, as it may contain otherwise too verbose information.
     *
     * @return The debug message, may be empty, but never {@code null}.
     */
    String getDebugMessage();
}
