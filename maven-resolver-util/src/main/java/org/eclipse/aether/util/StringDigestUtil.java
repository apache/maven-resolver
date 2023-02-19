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
package org.eclipse.aether.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A simple digester utility for Strings. Uses {@link MessageDigest} for requested algorithm. Supports one-pass or
 * several rounds of updates, and as result emits hex encoded String.
 *
 * @since 1.9.0
 */
public final class StringDigestUtil {
    private final MessageDigest digest;

    /**
     * Constructs instance with given algorithm.
     *
     * @see #sha1()
     * @see #sha1(String)
     */
    public StringDigestUtil(final String alg) {
        try {
            this.digest = MessageDigest.getInstance(alg);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Not supported digest algorithm: " + alg);
        }
    }

    /**
     * Updates instance with passed in string.
     */
    public StringDigestUtil update(String data) {
        if (data != null && !data.isEmpty()) {
            digest.update(data.getBytes(StandardCharsets.UTF_8));
        }
        return this;
    }

    /**
     * Returns the digest of all strings passed via {@link #update(String)} as hex string. There is no state preserved
     * and due implementation of {@link MessageDigest#digest()}, same applies here: this instance "resets" itself.
     * Hence, the digest hex encoded string is returned only once.
     *
     * @see MessageDigest#digest()
     */
    public String digest() {
        return ChecksumUtils.toHexString(digest.digest());
    }

    /**
     * Helper method to create {@link StringDigestUtil} using SHA-1 digest algorithm.
     */
    public static StringDigestUtil sha1() {
        return new StringDigestUtil("SHA-1");
    }

    /**
     * Helper method to calculate SHA-1 digest and hex encode it.
     */
    public static String sha1(final String string) {
        return sha1().update(string).digest();
    }
}
