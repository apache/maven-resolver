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
package org.eclipse.aether.impl;

/**
 * A ready-made Guice module that sets up bindings for all components from this library. To acquire a complete
 * repository system, clients need to bind an artifact descriptor reader, a version resolver, a version range resolver,
 * zero or more metadata generator factories, some repository connector and transporter factories to access remote
 * repositories.
 * 
 * @deprecated Use {@link org.eclipse.aether.impl.guice.AetherModule} instead.
 */
@Deprecated
public final class AetherModule
    extends org.eclipse.aether.impl.guice.AetherModule
{

}
