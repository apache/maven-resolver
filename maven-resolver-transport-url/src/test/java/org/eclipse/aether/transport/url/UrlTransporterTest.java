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
package org.eclipse.aether.transport.url;

import java.util.stream.Stream;

import org.eclipse.aether.internal.test.util.http.HttpTransporterTest;
import org.eclipse.aether.spi.io.PathProcessorSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * URL Transporter UT.
 */
class UrlTransporterTest extends HttpTransporterTest {

    public UrlTransporterTest() {
        super(() -> new UrlTransporterFactory(standardChecksumExtractor(), new PathProcessorSupport()));
    }

    @Override
    protected Stream<String> supportedCompressionAlgorithms() {
        return Stream.of("gzip", "deflate");
    }

    @Override
    protected boolean exposeContentCodingInTransportProperties() {
        return false;
    }

    @Override
    protected boolean supportsHttp3() {
        return false;
    }

    @Override
    protected boolean supportsHttp2() {
        return false;
    }

    @Override
    @Disabled("HTTP2 unsupported")
    @Test
    protected void testGet_HTTPS_HTTP2Only_Insecure_SecurityMode() {}

    @Override
    @Disabled("Controlled via Java system properties")
    @Test
    protected void testGet_HTTPS_Insecure_SecurityMode() {}

    @Override
    @Disabled("Controlled via Java system properties")
    @Test
    protected void testGet_HTTPS_Unknown_SecurityMode() {}

    @Override
    @Disabled("RFC9457 unsupported")
    @Test
    protected void testGet_AcceptsRfc9457() {}

    @Override
    @Disabled("RFC9457 unsupported")
    @Test
    protected void testGet_ParseRfc9457() {}

    @Override
    @Disabled("Resume unsupported")
    @Test
    protected void testGet_Resume() {}

    @Override
    @Disabled("RFC9457 unsupported")
    @Test
    protected void testGet_RFC9457Response() {}

    @Override
    @Disabled("RFC9457 unsupported")
    @Test
    protected void testGet_RFC9457Response_with_missing_fields() {}

    @Override
    @Disabled("RFC9457 unsupported")
    @Test
    protected void testPeek_DoesNotAcceptRfc9457() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testGetPut_AuthCache() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_AcceptsRfc9457() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_AuthCache() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_AuthCache_Preemptive() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_Authenticated_ExpectContinue() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_Authenticated_ExpectContinueBroken() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_Authenticated_ExpectContinueDisabled() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_Authenticated_ExpectContinueRejected() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_Authenticated_ExpectContinueRejected_ExplicitlyConfiguredHeader() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_EmptyResource() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_EncodedResourcePath() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_FileHandleLeak() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_FromFile() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_FromMemory() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_PreemptiveIsDefault() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_ProgressCancelled() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_ProxyAuthenticated() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_ProxyUnauthenticated() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_SSL() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_StartCancelled() {}

    @Override
    @Disabled("PUT unsupported")
    @Test
    protected void testPut_Unauthenticated() {}

    @Override
    @Disabled
    @Test
    protected void testRetryHandler_defaultCount_positive() {}

    @Override
    @Disabled
    @Test
    protected void testRetryHandler_explicitCount_positive() {}

    @Override
    @Disabled
    @Test
    protected void testRetryHandler_tooManyRequests_explicitCount_negative() {}

    @Override
    @Disabled
    @Test
    protected void testRetryHandler_tooManyRequests_explicitCount_positive() {}
}
