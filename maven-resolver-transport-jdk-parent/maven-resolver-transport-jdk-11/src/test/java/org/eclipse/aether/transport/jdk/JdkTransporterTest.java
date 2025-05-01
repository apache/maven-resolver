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
package org.eclipse.aether.transport.jdk;

import java.net.ConnectException;
import java.net.URI;

import org.eclipse.aether.internal.impl.DefaultPathProcessor;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.internal.test.util.http.HttpTransporterTest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * JDK Transporter UT.
 */
class JdkTransporterTest extends HttpTransporterTest {

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
    protected void testAuthSchemePreemptive() {}

    @Override
    @Disabled
    @Test
    protected void testPut_AuthCache_Preemptive() {}

    @Override
    @Disabled
    @Test
    protected void testPut_Unauthenticated() {}

    @Override
    @Disabled
    @Test
    protected void testPut_PreemptiveIsDefault() {}

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
    protected void testPut_Authenticated_ExpectContinueRejected_ExplicitlyConfiguredHeader() {}

    @Override
    @Disabled
    @Test
    protected void testRequestTimeout() throws Exception {}

    public JdkTransporterTest() {
        super(() -> new JdkTransporterFactory(standardChecksumExtractor(), new DefaultPathProcessor()));
    }

    @Override
    protected void enableHttp2Protocol() {
        session.setConfigProperty(JdkTransporterConfigurationKeys.CONFIG_PROP_HTTP_VERSION, "HTTP_2");
    }

    @Test
    void enhanceConnectExceptionMessages() {
        String uri = "https://localhost:12345/";
        RemoteRepository remoteRepository = new RemoteRepository.Builder("central", "default", uri).build();
        JdkTransporterFactory factory = new JdkTransporterFactory(s -> null, new DefaultPathProcessor());

        try (Transporter transporter = factory.newInstance(TestUtils.newSession(), remoteRepository)) {
            transporter.peek(new PeekTask(URI.create("repo/file.txt")));
            fail("This should throw");
        } catch (ConnectException e) {
            assertTrue(e.getMessage().contains("Connection to " + uri + " refused"), e.getMessage());
        } catch (Exception e) {
            fail("We expect ConnectException");
        }
    }
}
