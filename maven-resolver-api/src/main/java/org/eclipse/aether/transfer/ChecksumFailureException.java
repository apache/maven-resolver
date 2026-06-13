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
package org.eclipse.aether.transfer;

import org.eclipse.aether.RepositoryException;

/**
 * Thrown in case of a checksum failure during an artifact/metadata download. This exception is usually thrown in
 * following cases:
 * <ul>
 *     <li>actual checksum <em>mismatch</em>, see {@link #mismatch(String, String, String)}</li>
 *     <li>lack of required checksums, see {@link #noneAvailable(String, String)}</li>
 *     <li>processing problem during checksum checks (ie IO problem), see {@link #processingFailure(String, Throwable)}</li>
 * </ul>
 * It is resolver expectation to provide most available information to caller.
 */
public class ChecksumFailureException extends RepositoryException {

    private final String expected;

    private final String expectedKind;

    private final String actual;

    private final boolean retryWorthy;

    /**
     * Use in case of checksum mismatch. Creates a new exception with the specified expected, expected kind and actual
     * checksum. The resulting exception is {@link #isRetryWorthy() retry-worthy}. The checksum match check should
     * have already happened, this method does not check for any kind of inequality.
     *
     * @param expected The expected checksum as declared by the hosting repository, may be {@code null}.
     * @param expectedKind The expected checksum kind, may be {@code null}.
     * @param actual The actual checksum as computed from the local bytes, may be {@code null}.
     * @since 2.0.19
     */
    public static ChecksumFailureException mismatch(String expected, String expectedKind, String actual) {
        return mismatchDetail(null, expected, expectedKind, actual);
    }

    /**
     * Use in case of checksum mismatch. Creates a new exception with the specified expected, expected kind and actual
     * checksum. The resulting exception is {@link #isRetryWorthy() retry-worthy}. The checksum match check should
     * have already happened, this method does not check for any kind of inequality.
     *
     * @param detail The extra detail/information regarding mismatch.
     * @param expected The expected checksum as declared by the hosting repository, may be {@code null}.
     * @param expectedKind The expected checksum kind, may be {@code null}.
     * @param actual The actual checksum as computed from the local bytes, may be {@code null}.
     * @since 2.0.19
     */
    public static ChecksumFailureException mismatchDetail(
            String detail, String expected, String expectedKind, String actual) {
        String message = "Checksum validation failed, expected '"
                + expected + "'" + (expectedKind == null ? "" : " (" + expectedKind + ")")
                + " but is actually '" + actual + "'";
        if (detail != null) {
            message = message + " (" + detail + ")";
        }
        return new ChecksumFailureException(message, null, expected, expectedKind, actual, true);
    }

    /**
     * Use in case of checksum not available. Optionally, one can specify which kind was not available.
     *
     * @param message The message.
     * @param expectedKind The expected checksum kind, may be {@code null}.
     * @since 2.0.19
     */
    public static ChecksumFailureException noneAvailable(String message, String expectedKind) {
        return new ChecksumFailureException(message, null, "", expectedKind == null ? "" : expectedKind, "", false);
    }

    /**
     * Use in case of error, for example IO problem during checksum processing, calculation and alike. Ideally, one
     * should specify cause as well.
     *
     * @param message The message.
     * @param cause The cause.
     * @since 2.0.19
     */
    public static ChecksumFailureException processingFailure(String message, Throwable cause) {
        return new ChecksumFailureException(message, cause, "", "", "", false);
    }

    /**
     * Creates a new exception with the specified expected, expected kind and actual checksum. The resulting exception
     * is {@link #isRetryWorthy() retry-worthy}.
     *
     * @param expected The expected checksum as declared by the hosting repository, may be {@code null}.
     * @param expectedKind The expected checksum kind, may be {@code null}.
     * @param actual The actual checksum as computed from the local bytes, may be {@code null}.
     * @since 1.8.0
     * @deprecated Use {@link #mismatch(String, String, String)} or other suitable helper method instead.
     */
    @Deprecated
    public ChecksumFailureException(String expected, String expectedKind, String actual) {
        super("Checksum validation failed, expected '"
                + expected + "'" + (expectedKind == null ? "" : " (" + expectedKind + ")")
                + " but is actually '" + actual + "'");
        this.expected = expected;
        this.expectedKind = expectedKind;
        this.actual = actual;
        this.retryWorthy = true;
    }

    /**
     * Creates a new exception with the specified detail message. The resulting exception is not
     * {@link #isRetryWorthy() retry-worthy}. Use this constructor ONLY in cases like "no data to work with",
     * like missing checksums. In every other case use some other constructor.
     *
     * @param message The detail message, may be {@code null}.
     * @deprecated Use {@link #noneAvailable(String, String)} or other suitable helper method instead.
     */
    @Deprecated
    public ChecksumFailureException(String message) {
        this(message, null, "", "", "", false);
    }

    /**
     * Creates a new exception with the specified cause. The resulting exception is not {@link #isRetryWorthy()
     * retry-worthy}. Use this constructor in case some other error (ie IO problem) prevented checksum calculation.
     *
     * @param cause The exception that caused this one, may be {@code null}.
     * @deprecated Use {@link #processingFailure(String, Throwable)} or other helper method instead.
     */
    @Deprecated
    public ChecksumFailureException(Throwable cause) {
        this("Checksum validation failed" + getMessage(": ", cause), cause, "", "", "", false);
    }

    /**
     * Creates a new exception with the specified detail message and cause. The resulting exception is not
     * {@link #isRetryWorthy() retry-worthy}. Use this constructor in case some other error (ie IO problem)
     * prevented checksum calculation.
     *
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     * @deprecated Use {@link #processingFailure(String, Throwable)} or other helper method instead.
     */
    @Deprecated
    public ChecksumFailureException(String message, Throwable cause) {
        this(message, cause, "", "", "", false);
    }

    /**
     * Creates a new exception with the specified retry flag, detail message and cause.
     *
     * @param retryWorthy {@code true} if the exception is retry-worthy, {@code false} otherwise.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     * @deprecated Do not use this constructor, it lacks information.
     */
    @Deprecated
    public ChecksumFailureException(boolean retryWorthy, String message, Throwable cause) {
        this(message, cause, "", "", "", retryWorthy);
    }

    /**
     * Hidden constructor, use static helper methods instead, suitable for your case.
     */
    private ChecksumFailureException(
            String message, Throwable cause, String expected, String expectedKind, String actual, boolean retryWorthy) {
        super(message, cause);
        this.expected = expected;
        this.expectedKind = expectedKind;
        this.actual = actual;
        this.retryWorthy = retryWorthy;
    }

    /**
     * Gets the expected checksum for the downloaded artifact/metadata.
     *
     * @return The expected checksum as declared by the hosting repository or {@code null} if unknown.
     */
    public String getExpected() {
        return expected;
    }

    /**
     * Gets the expected checksum kind for the downloaded artifact/metadata.
     *
     * @return The expected checksum kind or {@code null} if unknown.
     * @since 1.8.0
     */
    public String getExpectedKind() {
        return expectedKind;
    }

    /**
     * Gets the actual checksum for the downloaded artifact/metadata.
     *
     * @return The actual checksum as computed from the local bytes or {@code null} if unknown.
     */
    public String getActual() {
        return actual;
    }

    /**
     * Indicates whether the corresponding download is retry-worthy.
     *
     * @return {@code true} if retrying the download might solve the checksum failure, {@code false} if the checksum
     *         failure is non-recoverable.
     */
    public boolean isRetryWorthy() {
        return retryWorthy;
    }
}
