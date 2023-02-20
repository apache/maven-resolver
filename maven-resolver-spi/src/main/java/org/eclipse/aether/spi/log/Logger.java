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
package org.eclipse.aether.spi.log;

/**
 * A simple logger to facilitate emission of diagnostic messages. In general, unrecoverable errors should be reported
 * via exceptions and informational notifications should be reported via events, hence this logger interface focuses on
 * support for tracing.
 *
 * @deprecated Use SLF4J instead
 */
@Deprecated
public interface Logger {

    /**
     * Indicates whether debug logging is enabled.
     *
     * @return {@code true} if debug logging is enabled, {@code false} otherwise.
     */
    boolean isDebugEnabled();

    /**
     * Emits the specified message.
     *
     * @param msg The message to log, must not be {@code null}.
     */
    void debug(String msg);

    /**
     * Emits the specified message along with a stack trace of the given exception.
     *
     * @param msg The message to log, must not be {@code null}.
     * @param error The exception to log, may be {@code null}.
     */
    void debug(String msg, Throwable error);

    /**
     * Indicates whether warn logging is enabled.
     *
     * @return {@code true} if warn logging is enabled, {@code false} otherwise.
     */
    boolean isWarnEnabled();

    /**
     * Emits the specified message.
     *
     * @param msg The message to log, must not be {@code null}.
     */
    void warn(String msg);

    /**
     * Emits the specified message along with a stack trace of the given exception.
     *
     * @param msg The message to log, must not be {@code null}.
     * @param error The exception to log, may be {@code null}.
     */
    void warn(String msg, Throwable error);
}
