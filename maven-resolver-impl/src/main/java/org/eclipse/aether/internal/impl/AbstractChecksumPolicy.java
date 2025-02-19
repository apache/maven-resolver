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
package org.eclipse.aether.internal.impl;

import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.transfer.TransferResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

abstract class AbstractChecksumPolicy implements ChecksumPolicy {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final TransferResource resource;

    protected AbstractChecksumPolicy(TransferResource resource) {
        this.resource = resource;
    }

    @Override
    public boolean onChecksumMatch(String algorithm, ChecksumKind kind) {
        requireNonNull(algorithm, "algorithm cannot be null");
        return true;
    }

    @Override
    public void onChecksumMismatch(String algorithm, ChecksumKind kind, ChecksumFailureException exception)
            throws ChecksumFailureException {
        requireNonNull(algorithm, "algorithm cannot be null");
        requireNonNull(exception, "exception cannot be null");
        throw exception;
    }

    @Override
    public void onChecksumError(String algorithm, ChecksumKind kind, ChecksumFailureException exception)
            throws ChecksumFailureException {
        requireNonNull(algorithm, "algorithm cannot be null");
        requireNonNull(exception, "exception cannot be null");
        logger.debug("Could not validate {} checksum for {}", algorithm, resource.getResourceName(), exception);
    }

    @Override
    public void onNoMoreChecksums() throws ChecksumFailureException {
        throw new ChecksumFailureException("Checksum validation failed, no checksums available");
    }

    @Override
    public void onTransferRetry() {}
}
