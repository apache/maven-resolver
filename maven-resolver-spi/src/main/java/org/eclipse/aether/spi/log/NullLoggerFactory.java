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
 * A logger factory that disables any logging.
 *
 * @deprecated Use SLF4J instead
 */
@Deprecated
public final class NullLoggerFactory implements LoggerFactory {

    /**
     * The singleton instance of this factory.
     */
    public static final LoggerFactory INSTANCE = new NullLoggerFactory();

    /**
     * The singleton logger used by this factory.
     */
    public static final Logger LOGGER = new NullLogger();

    public Logger getLogger(String name) {
        return LOGGER;
    }

    private NullLoggerFactory() {
        // hide constructor
    }

    /**
     * Gets a logger from the specified factory for the given class, falling back to a logger from this factory if the
     * specified factory is {@code null} or fails to provide a logger.
     *
     * @param loggerFactory The logger factory from which to get the logger, may be {@code null}.
     * @param type The class for which to get the logger, must not be {@code null}.
     * @return The requested logger, never {@code null}.
     */
    public static Logger getSafeLogger(LoggerFactory loggerFactory, Class<?> type) {
        if (loggerFactory == null) {
            return LOGGER;
        }
        Logger logger = loggerFactory.getLogger(type.getName());
        if (logger == null) {
            return LOGGER;
        }
        return logger;
    }
}
