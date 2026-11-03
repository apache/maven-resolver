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

import javax.net.ssl.SSLHandshakeException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeoutException;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JettyRetryHandlerTest {
    private final DefaultRepositorySystemSession session = TestUtils.newSession();

    private final RemoteRepository repository =
            new RemoteRepository.Builder("test", "default", "http://localhost/repo").build();

    private JettyRetryHandler newHandler() {
        return new JettyRetryHandler(session, repository);
    }

    private static HttpFields retryAfter(String value) {
        return HttpFields.build().put(HttpHeader.RETRY_AFTER, value).asImmutable();
    }

    @Test
    void retriesAreLimitedByRetryCount() {
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT, 2);
        JettyRetryHandler handler = newHandler();
        assertEquals(2, handler.getRetryCount());
        assertTrue(handler.shouldRetry(0, 503));
        assertTrue(handler.shouldRetry(1, 503));
        assertFalse(handler.shouldRetry(2, 503));
        assertTrue(handler.shouldRetry(1, new EOFException()));
        assertFalse(handler.shouldRetry(2, new EOFException()));
    }

    @Test
    void noRetriesIfDisabled() {
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT, 0);
        JettyRetryHandler handler = newHandler();
        assertFalse(handler.shouldRetry(0, 503));
        assertFalse(handler.shouldRetry(0, new EOFException()));
    }

    @Test
    void onlyConfiguredStatusCodesAreRetried() {
        JettyRetryHandler handler = newHandler();
        assertTrue(handler.shouldRetry(0, 429));
        assertTrue(handler.shouldRetry(0, 503));
        assertFalse(handler.shouldRetry(0, 404));
        assertFalse(handler.shouldRetry(0, 500));

        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE, "500,502");
        handler = newHandler();
        assertTrue(handler.shouldRetry(0, 500));
        assertTrue(handler.shouldRetry(0, 502));
        assertFalse(handler.shouldRetry(0, 503));
    }

    @Test
    void transientFailuresAreRetried() {
        JettyRetryHandler handler = newHandler();
        assertTrue(handler.shouldRetry(0, new EOFException()));
        assertTrue(handler.shouldRetry(0, new ClosedChannelException()));
        assertTrue(handler.shouldRetry(0, new IOException("some failure")));
        assertTrue(handler.shouldRetry(0, new TimeoutException("Idle timeout expired")));
    }

    @Test
    void permanentFailuresAreNotRetried() {
        JettyRetryHandler handler = newHandler();
        assertFalse(handler.shouldRetry(0, new UnknownHostException()));
        assertFalse(handler.shouldRetry(0, new ConnectException()));
        assertFalse(handler.shouldRetry(0, new InterruptedIOException()));
        assertFalse(handler.shouldRetry(0, new SSLHandshakeException("bad certificate")));
        assertFalse(handler.shouldRetry(0, new IllegalStateException()));
    }

    @Test
    void cancelledTransferIsNotRetried() {
        JettyRetryHandler handler = newHandler();
        assertFalse(handler.shouldRetry(0, new IOException(new TransferCancelledException())));
    }

    @Test
    void refusedRedirectIsNotRetried() {
        JettyRetryHandler handler = newHandler();
        assertFalse(handler.shouldRetry(0, new RedirectRefusedException("refused")));
        assertFalse(handler.shouldRetry(0, new IOException(new RedirectRefusedException("refused"))));
    }

    @Test
    void delayGrowsLinearlyWithAttempts() {
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL, 100L);
        JettyRetryHandler handler = newHandler();
        assertEquals(100L, handler.getDelay(HttpFields.build(), 0).millis());
        assertEquals(200L, handler.getDelay(HttpFields.build(), 1).millis());
        assertEquals(300L, handler.getDelay(HttpFields.build(), 2).millis());
        assertEquals(
                "retryInterval 100 ms multiplied by execution count 2",
                handler.getDelay(HttpFields.build(), 1).reason());
    }

    @Test
    void retryAfterSecondsIsObeyed() {
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL, 5000L);
        JettyRetryHandler.Delay delay = newHandler().getDelay(retryAfter("2"), 0);
        assertEquals(2000L, delay.millis());
        assertEquals("Retry-After header with seconds value 2", delay.reason());
    }

    @Test
    void retryAfterDateIsObeyed() {
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                Instant.now().plusSeconds(60).atOffset(ZoneOffset.UTC));
        JettyRetryHandler.Delay delay = newHandler().getDelay(retryAfter(date), 0);
        assertTrue(delay.millis() > 50_000L && delay.millis() <= 60_000L, "Unexpected delay " + delay.millis());
        assertEquals("Retry-After header with date value " + date, delay.reason());
    }

    @Test
    void retryAfterDateInThePastMeansNoDelay() {
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                Instant.now().minusSeconds(60).atOffset(ZoneOffset.UTC));
        assertEquals(0L, newHandler().getDelay(retryAfter(date), 0).millis());
    }

    @Test
    void invalidRetryAfterFallsBackToRetryInterval() {
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL, 100L);
        JettyRetryHandler handler = newHandler();
        assertEquals(100L, handler.getDelay(retryAfter("soon"), 0).millis());
        assertEquals(100L, handler.getDelay(retryAfter("Not a date: really"), 0).millis());
    }

    @Test
    void delayIsCappedAtMaximum() {
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL_MAX, 1500L);
        JettyRetryHandler.Delay delay = newHandler().getDelay(retryAfter("3600"), 0);
        assertEquals(1500L, delay.millis());
        assertEquals("Retry-After header with seconds value 3600, capped at max value", delay.reason());
    }
}
