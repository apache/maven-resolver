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

import org.eclipse.aether.internal.test.util.http.HttpTransporterTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Jetty transporter UT.
 */
class JettyTransporterTest extends HttpTransporterTest {

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
    protected void testPut_Authenticated_ExpectContinueRejected_ExplicitlyConfiguredHeader() {}

    public JettyTransporterTest() {
        super(JettyTransporterFactory::new);
    }
}
