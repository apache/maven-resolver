package org.eclipse.aether.spi.connector.checksum;

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

import org.eclipse.aether.transfer.ChecksumFailureException;

/**
 * A checksum policy gets employed by repository connectors to validate the integrity of a downloaded file. For each
 * downloaded file, a checksum policy instance is obtained and presented with the available checksums to conclude
 * whether the download is valid or not. The following pseudo-code illustrates the usage of a checksum policy by a
 * repository connector in some more detail (the retry logic has been omitted for the sake of brevity):
 *
 * <pre>
 * void validateChecksums() throws ChecksumFailureException {
 *   for (checksum : checksums) {
 *     switch (checksum.state) {
 *       case MATCH:
 *         if (policy.onChecksumMatch(...)) {
 *           return;
 *         }
 *         break;
 *       case MISMATCH:
 *         policy.onChecksumMismatch(...);
 *         break;
 *       case ERROR:
 *         policy.onChecksumError(...);
 *         break;
 *     }
 *   }
 *   policy.onNoMoreChecksums();
 * }
 *
 * void downloadFile() throws Exception {
 *   ...
 *   policy = newChecksumPolicy();
 *   try {
 *     validateChecksums();
 *   } catch (ChecksumFailureException e) {
 *     if (!policy.onTransferChecksumFailure(...)) {
 *       throw e;
 *     }
 *   }
 * }
 * </pre>
 * <p>
 * Checksum policies might be stateful and are generally not thread-safe.
 */
public interface ChecksumPolicy
{

    /**
     * Bit flag indicating a checksum which is not part of the official repository layout/structure.
     */
    int KIND_UNOFFICIAL = 0x01;

    /**
     * Signals a match between the locally computed checksum value and the checksum value declared by the remote
     * repository.
     *
     * @param algorithm The name of the checksum algorithm being used, must not be {@code null}.
     * @param kind      A bit field providing further details about the checksum. See the {@code KIND_*} constants in
     *                  this interface for possible bit flags.
     * @return {@code true} to accept the download as valid and stop further validation, {@code false} to continue
     * validation with the next checksum.
     */
    boolean onChecksumMatch( String algorithm, int kind );

    /**
     * Signals a mismatch between the locally computed checksum value and the checksum value declared by the remote
     * repository. A simple policy would just rethrow the provided exception. More sophisticated policies could update
     * their internal state and defer a conclusion until all available checksums have been processed.
     *
     * @param algorithm The name of the checksum algorithm being used, must not be {@code null}.
     * @param kind      A bit field providing further details about the checksum. See the {@code KIND_*} constants in
     *                  this
     *                  interface for possible bit flags.
     * @param exception The exception describing the checksum mismatch, must not be {@code null}.
     * @throws ChecksumFailureException If the checksum validation is to be failed. If the method returns normally,
     *                                  validation continues with the next checksum.
     */
    void onChecksumMismatch( String algorithm, int kind, ChecksumFailureException exception )
            throws ChecksumFailureException;

    /**
     * Signals an error while computing the local checksum value or retrieving the checksum value from the remote
     * repository.
     *
     * @param algorithm The name of the checksum algorithm being used, must not be {@code null}.
     * @param kind      A bit field providing further details about the checksum. See the {@code KIND_*} constants in
     *                  this
     *                  interface for possible bit flags.
     * @param exception The exception describing the checksum error, must not be {@code null}.
     * @throws ChecksumFailureException If the checksum validation is to be failed. If the method returns normally,
     *                                  validation continues with the next checksum.
     */
    void onChecksumError( String algorithm, int kind, ChecksumFailureException exception )
            throws ChecksumFailureException;

    /**
     * Signals that all available checksums have been processed.
     *
     * @throws ChecksumFailureException If the checksum validation is to be failed. If the method returns normally, the
     *                                  download is assumed to be valid.
     */
    void onNoMoreChecksums()
            throws ChecksumFailureException;

    /**
     * Signals that the download is being retried after a previously thrown {@link ChecksumFailureException} that is
     * {@link ChecksumFailureException#isRetryWorthy() retry-worthy}. Policies that maintain internal state will usually
     * have to reset some of this state at this point to prepare for a new round of validation.
     */
    void onTransferRetry();

    /**
     * Signals that (even after a potential retry) checksum validation has failed. A policy could opt to merely log this
     * issue or insist on rejecting the downloaded file as unusable.
     *
     * @param exception The exception that was thrown from a prior call to
     *                  {@link #onChecksumMismatch(String, int, ChecksumFailureException)},
     *                  {@link #onChecksumError(String, int, ChecksumFailureException)} or {@link
     *                  #onNoMoreChecksums()}.
     * @return {@code true} to accept the download nevertheless and let artifact resolution succeed, {@code false} to
     * reject the transferred file as unusable.
     */
    boolean onTransferChecksumFailure( ChecksumFailureException exception );

}
