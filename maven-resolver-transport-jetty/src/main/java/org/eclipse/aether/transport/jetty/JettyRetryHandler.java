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
package org.eclipse.aether.transport.jetty;

import javax.net.ssl.SSLException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.util.connector.transport.http.HttpTransporterUtils;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides whether a failed request is retried and how long to wait before doing so, based on the
 * {@code aether.connector.http.retryHandler.*} configuration. The Jetty HTTP client has no retry hook of its own,
 * therefore {@link JettyTransporter} drives the retries and consults this class.
 */
final class JettyRetryHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JettyRetryHandler.class);

    /**
     * Classes of IOExceptions that should not be retried (because they are permanent failures).
     * Same as in <a href="https://github.com/apache/httpcomponents-client/blob/54900db4653d7f207477e6ee40135b88e9bcf832/httpclient/src/main/java/org/apache/http/impl/client/DefaultHttpRequestRetryHandler.java#L102">
     * Apache HttpClient's DefaultHttpRequestRetryHandler</a>.
     */
    private static final Set<Class<? extends IOException>> NON_RETRIABLE_IO_EXCEPTIONS = Set.of(
            InterruptedIOException.class,
            UnknownHostException.class,
            ConnectException.class,
            NoRouteToHostException.class,
            SSLException.class);

    /**
     * The delay before a retry together with the reason for choosing it.
     */
    record Delay(long millis, String reason) {}

    private final int retryCount;

    private final long retryInterval;

    private final long retryIntervalMax;

    private final Set<Integer> retriableStatusCodes;

    JettyRetryHandler(RepositorySystemSession session, RemoteRepository repository) {
        this.retryCount = HttpTransporterUtils.getHttpRetryHandlerCount(session, repository);
        this.retryInterval = HttpTransporterUtils.getHttpRetryHandlerInterval(session, repository);
        this.retryIntervalMax = HttpTransporterUtils.getHttpRetryHandlerIntervalMax(session, repository);
        this.retriableStatusCodes = HttpTransporterUtils.getHttpServiceUnavailableCodes(session, repository);
    }

    /**
     * The maximum number of retries, i.e. a request is attempted at most {@code getRetryCount() + 1} times.
     */
    int getRetryCount() {
        return retryCount;
    }

    /**
     * Tells whether a request that failed with the given exception should be sent again.
     *
     * @param attempt the 0-based number of the attempt that failed
     */
    boolean shouldRetry(int attempt, Throwable failure) {
        return attempt < retryCount && isRetriable(failure);
    }

    /**
     * Tells whether a request that got a response with the given status code should be sent again.
     *
     * @param attempt the 0-based number of the attempt that got the response
     */
    boolean shouldRetry(int attempt, int statusCode) {
        return attempt < retryCount && retriableStatusCodes.contains(statusCode);
    }

    private static boolean isRetriable(Throwable failure) {
        if (failure instanceof TimeoutException) {
            return true;
        }
        if (!(failure instanceof IOException)) {
            return false;
        }
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof TransferCancelledException || t instanceof RedirectRefusedException) {
                return false;
            }
        }
        return NON_RETRIABLE_IO_EXCEPTIONS.stream().noneMatch(clazz -> clazz.isInstance(failure));
    }

    /**
     * Computes the delay before the next attempt. A {@code Retry-After} response header is obeyed, otherwise the
     * delay grows linearly with the number of attempts. It never exceeds the configured maximum.
     *
     * @param responseHeaders the headers of the response that triggered the retry
     * @param attempt the 0-based number of the attempt that got the response
     */
    Delay getDelay(HttpFields responseHeaders, int attempt) {
        long delay = -1L;
        String reason = "";
        String retryAfter = responseHeaders.get(HttpHeader.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                if (retryAfter.contains(":")) { // is date when to retry
                    delay = Math.max(
                            responseHeaders.getDateField(HttpHeader.RETRY_AFTER) - System.currentTimeMillis(), 0L);
                    reason = "Retry-After header with date value " + retryAfter;
                } else {
                    delay = Long.parseLong(retryAfter.trim()) * 1000L; // is in seconds
                    reason = "Retry-After header with seconds value " + retryAfter;
                }
            } catch (IllegalArgumentException e) { // includes NumberFormatException
                LOGGER.warn("Invalid Retry-After header value {}", retryAfter);
                delay = -1L;
            }
        }
        if (delay < 0L) {
            long executionCount = attempt + 1L;
            delay = executionCount * retryInterval;
            reason = "retryInterval " + retryInterval + " ms multiplied by execution count " + executionCount;
        }
        if (delay > retryIntervalMax) {
            delay = retryIntervalMax;
            reason += ", capped at max value";
        }
        return new Delay(delay, reason);
    }
}
