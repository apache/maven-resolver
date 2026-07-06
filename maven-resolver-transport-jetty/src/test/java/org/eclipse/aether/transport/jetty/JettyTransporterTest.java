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

import java.util.stream.Stream;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.internal.test.util.http.HttpTransporterTest;
import org.eclipse.aether.spi.io.PathProcessorSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Jetty transporter UT.
 */
class JettyTransporterTest extends HttpTransporterTest {

    @Override
    protected Stream<String> supportedCompressionAlgorithms() {
        return Stream.of("gzip", "deflate", "br", "zstd");
    }

    @Override
    @Disabled
    @Test
    protected void testAuthSchemeReuse() {}

    @Override
    @Disabled
    @Test
    protected void testPut_ProxyUnauthenticated() {}

    @Override
    @Disabled
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
    protected void testRetryHandler_tooManyRequests_explicitCount_positive() {}

    @Override
    @Disabled
    @Test
    protected void testRetryHandler_tooManyRequests_explicitCount_negative() {}

    @Override
    @Disabled
    @Test
    protected void testPut_Authenticated_ExpectContinueRejected_ExplicitlyConfiguredHeader() {}

    @Override
    @Test
    protected void testGet_HTTP3() throws Exception {
        // Jetty's HTTP/3 support is based on Quiche which does not consider the default SSL context (https://github.com/jetty/jetty.project/issues/15370)
        session.setConfigProperty(
                ConfigurationProperties.HTTPS_SECURITY_MODE, ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE);
        super.testGet_HTTP3();
    }

    public JettyTransporterTest() {
        super(() -> new JettyTransporterFactory(standardChecksumExtractor(), new PathProcessorSupport()));
    }
}
