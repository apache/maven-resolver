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
package org.eclipse.aether.internal.test.util.http;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

final class RetryLogCapture implements AutoCloseable {
    private final Logger logger;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    RetryLogCapture(Class<?> transporterClass) {
        logger = (Logger) LoggerFactory.getLogger(transporterClass);
        appender.start();
        logger.addAppender(appender);
    }

    /**
     * Returns the captured log messages that contain "Retrying" and are at WARN level.
     *
     * @return a list of log messages
     */
    List<String> messages() {
        return appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains("Retrying"))
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
