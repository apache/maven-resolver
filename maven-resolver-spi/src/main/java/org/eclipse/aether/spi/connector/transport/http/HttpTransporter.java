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
package org.eclipse.aether.spi.connector.transport.http;

import java.net.URI;

import org.eclipse.aether.spi.connector.transport.Transporter;
import org.slf4j.Logger;

/**
 * A transporter using HTTP protocol.
 *
 * @since 2.0.0
 */
public interface HttpTransporter extends Transporter {
    @Override
    default int classify(Throwable error) {
        if (error instanceof HttpTransporterException) {
            int statusCode = ((HttpTransporterException) error).getStatusCode();
            if (statusCode == HttpConstants.NOT_FOUND || statusCode == HttpConstants.GONE) {
                return ERROR_NOT_FOUND;
            }
        }
        return ERROR_OTHER;
    }

    /**
     *
     * @param logger
     * @param method
     * @param uri
     * @param requestAttempt the 0-based counter of the previous request attempt (0 for the first attempt, 1 for the first retry, etc.)
     * @param maxRequestAttempt the maximum number of request attempts (this is including this original request)
     * @param exception the exception that caused the immediate retry
     */
    static void logImmediateRetry(
            Logger logger, String method, URI uri, int requestAttempt, int maxRequestAttempt, Throwable exception) {
        String reason = String.format("%s: %s", exception.getClass().getName(), exception.getMessage());
        logRetry(logger, method, uri, requestAttempt, maxRequestAttempt, reason, 0L, null);
    }

    /**
     *
     * @param logger
     * @param method
     * @param uri
     * @param requestAttempt the 0-based counter of the previous request attempt (0 for the first attempt, 1 for the first retry, etc.)
     * @param maxRequestAttempt the maximum number of request attempts (this is including this original request)
     * @param reason the reason for the retry (usually a status code or an exception)
     * @param retryDelay the interval in milliseconds before the next retry (0 or negative for immediate retry)
     * @param retryDelayReason the reason for the retry interval (may be empty if unknown)
     */
    @SuppressWarnings("checkstyle:parameternumber")
    static void logRetry(
            Logger logger,
            String method,
            URI uri,
            int requestAttempt,
            int maxRequestAttempt,
            String reason,
            long retryDelay,
            String retryDelayReason) {

        final String retryingDelayMessage;
        if (retryDelay > 0L) {
            if (retryDelayReason != null && !retryDelayReason.isEmpty()) {
                retryingDelayMessage = "Retrying in " + retryDelay + " ms (due to " + retryDelayReason + ")";
            } else {
                retryingDelayMessage = "Retrying in " + retryDelay + " ms";
            }
        } else {
            retryingDelayMessage = "Retrying immediately";
        }
        logger.warn(
                "{} request to {} failed (attempt {} of {}) due to {}. {}...",
                method,
                uri,
                requestAttempt + 1,
                maxRequestAttempt,
                reason,
                retryingDelayMessage);
    }
}
