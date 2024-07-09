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
package org.eclipse.aether.named.ipc;

/**
 * Constants used for the inter-process communication protocol.
 *
 * @since 2.0.1
 */
public class IpcMessages {

    public static final String REQUEST_CONTEXT = "request-context";
    public static final String REQUEST_ACQUIRE = "request-acquire";
    public static final String REQUEST_CLOSE = "request-close";
    public static final String REQUEST_STOP = "request-stop";
    public static final String RESPONSE_CONTEXT = "response-context";
    public static final String RESPONSE_ACQUIRE = "response-acquire";
    public static final String RESPONSE_CLOSE = "response-close";
    public static final String RESPONSE_STOP = "response-stop";
}
