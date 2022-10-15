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
package org.eclipse.aether.util.repository;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.Objects;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;

/**
 * Authentication block that manages a single authentication key and its string value.
 */
final class StringAuthentication implements Authentication {

    private final String key;

    private final String value;

    StringAuthentication(String key, String value) {
        this.key = requireNonNull(key, "authentication key cannot be null");
        if (key.length() == 0) {
            throw new IllegalArgumentException("authentication key cannot be empty");
        }
        this.value = value;
    }

    public void fill(AuthenticationContext context, String key, Map<String, String> data) {
        requireNonNull(context, "context cannot be null");
        context.put(this.key, value);
    }

    public void digest(AuthenticationDigest digest) {
        requireNonNull(digest, "digest cannot be null");
        digest.update(key, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !getClass().equals(obj.getClass())) {
            return false;
        }
        StringAuthentication that = (StringAuthentication) obj;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + key.hashCode();
        hash = hash * 31 + ((value != null) ? value.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }
}
