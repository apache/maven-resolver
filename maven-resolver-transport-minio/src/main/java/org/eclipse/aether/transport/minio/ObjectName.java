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
package org.eclipse.aether.transport.minio;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * S3 Object name, bucket + name.
 *
 * @since 2.0.2
 */
public final class ObjectName {
    private final String bucket;
    private final String name;
    private final int hashCode;

    public ObjectName(String bucket, String name) {
        this.bucket = requireNonNull(bucket);
        this.name = requireNonNull(name);

        if (bucket.contains("/")) {
            throw new IllegalArgumentException("invalid bucket name: " + bucket);
        }
        if (name.contains("\\")) {
            throw new IllegalArgumentException("invalid object name: " + name);
        }

        this.hashCode = Objects.hash(bucket, name);
    }

    public String getBucket() {
        return bucket;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ObjectName that = (ObjectName) o;
        return Objects.equals(bucket, that.bucket) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return bucket + "/" + name;
    }

    public static String normalize(String name) {
        return name.replace('\\', '/');
    }
}
