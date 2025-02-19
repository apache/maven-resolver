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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySelector;

public final class Checksums {
    private Checksums() {
        // nop
    }

    public static Map<String, ChecksumAlgorithmFactory> checksumAlgorithms() {
        HashMap<String, ChecksumAlgorithmFactory> factories = new HashMap<>();
        factories.put(Sha512ChecksumAlgorithmFactory.NAME, new Sha512ChecksumAlgorithmFactory());
        factories.put(Sha256ChecksumAlgorithmFactory.NAME, new Sha256ChecksumAlgorithmFactory());
        factories.put(Sha1ChecksumAlgorithmFactory.NAME, new Sha1ChecksumAlgorithmFactory());
        factories.put(Md5ChecksumAlgorithmFactory.NAME, new Md5ChecksumAlgorithmFactory());
        return factories;
    }

    public static ChecksumAlgorithmFactorySelector checksumsSelector() {
        return new DefaultChecksumAlgorithmFactorySelector(checksumAlgorithms());
    }
}
