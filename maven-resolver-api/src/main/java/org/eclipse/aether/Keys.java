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
package org.eclipse.aether;

import java.util.Arrays;
import java.util.Collection;

import static java.util.Objects.requireNonNull;

/**
 * A helper class to create keys, to be used with {@link SessionData} and {@link RepositoryCache} instances as keys.
 *
 * It is this class user responsibility to use proper objects as keys, this class merely helps one to create composite
 * keys.
 *
 * Important: never forget to perform {@code null}-check, when getting cache from {@link RepositorySystemSession#getCache()}
 * as it may be disabled session-wise.
 *
 * @see RepositorySystemSession#getData()
 * @see RepositorySystemSession#getCache()
 * @since 2.0.19
 */
public final class Keys {
    private Keys() {}

    public static Object of(Object... keys) {
        return new CompositeKey(keys);
    }

    private static final class CompositeKey {
        private final Object[] keys;
        private final int hashCode;

        private CompositeKey append(Object... appendedKeys) {
            requireNonNull(appendedKeys, "appended keys cannot be null");
            Object[] newKeys = new Object[this.keys.length + appendedKeys.length];
            System.arraycopy(this.keys, 0, newKeys, 0, this.keys.length);
            System.arraycopy(appendedKeys, 0, newKeys, this.keys.length, appendedKeys.length);
            return new CompositeKey(newKeys);
        }

        private CompositeKey(Object... keys) {
            this.keys = requireNonNull(keys, "keys cannot be null");
            this.hashCode = Arrays.hashCode(keys);
            if (this.keys.length == 0) {
                throw new IllegalArgumentException("composite key must have at least one element");
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof CompositeKey)) {
                return false;
            }
            CompositeKey that = (CompositeKey) obj;
            return Arrays.equals(keys, that.keys);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
