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
package org.eclipse.aether.internal.impl.checksum;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * The SHA-256 checksum type.
 *
 * @since 1.8.0
 */
@Singleton
@Named(Sha256ChecksumAlgorithmFactory.NAME)
public class Sha256ChecksumAlgorithmFactory extends MessageDigestChecksumAlgorithmFactorySupport {
    public static final String NAME = "SHA-256";

    @Inject
    public Sha256ChecksumAlgorithmFactory() {
        super(NAME, "sha256");
    }
}
