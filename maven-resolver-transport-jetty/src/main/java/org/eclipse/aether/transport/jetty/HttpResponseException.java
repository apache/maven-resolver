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

import java.io.IOException;

import org.eclipse.jetty.client.api.Response;

/**
 * Internal exception.
 */
final class HttpResponseException extends IOException
{
    private final Response response;

    HttpResponseException( Response response, String message )
    {
        super( message );
        this.response = response;
    }

    public Response getResponse()
    {
        return response;
    }

    public int getStatusCode()
    {
        return response.getStatus();
    }
}
