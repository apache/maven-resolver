package org.eclipse.aether.transport.jetty;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Map;

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

/**
 * A component extracting included checksums from response of artifact request.
 *
 * @since 1.9.3
 */
public abstract class ChecksumExtractor
{
    /**
     * Prepares request, if needed.
     */
    public void prepareRequest( Request request )
    {
        // nothing
    }

    /**
     * May control is request to be retried with checksum extractors disabled.
     */
    public boolean retryWithoutExtractor( HttpResponseException exception )
    {
        return false; // nothing, usually tied to prepareRequest
    }

    /**
     * Tries to extract checksums from response headers, if present, otherwise returns {@code null}.
     */
    public abstract Map<String, String> extractChecksums( Response response );
}
