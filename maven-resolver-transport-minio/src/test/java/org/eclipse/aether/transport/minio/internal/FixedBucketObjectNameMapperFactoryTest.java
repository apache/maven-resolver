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
package org.eclipse.aether.transport.minio.internal;

import java.util.Collections;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transport.minio.MinioTransporterConfigurationKeys;
import org.eclipse.aether.transport.minio.ObjectNameMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UT asserting the effective bucket is exactly the one the operator configured, with the documented default:
 * a swapped {@code ConfigUtils.getString} argument order previously made the effective bucket the literal
 * configuration key string and ignored the documented global key.
 */
class FixedBucketObjectNameMapperFactoryTest {
    private final FixedBucketObjectNameMapperFactory factory = new FixedBucketObjectNameMapperFactory();

    private final RemoteRepository repository =
            new RemoteRepository.Builder("repo", "default", "minio+https://localhost").build();

    private String effectiveBucket(DefaultRepositorySystemSession session) {
        ObjectNameMapper mapper = factory.create(session, repository, null, Collections.emptyMap());
        return mapper.name("g/a/v/a.jar").getBucket();
    }

    @Test
    void documentedDefaultBucketNameIsUsed() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(h -> true);
        assertEquals(MinioTransporterConfigurationKeys.DEFAULT_FIXED_BUCKET_NAME, effectiveBucket(session));
    }

    @Test
    void globalConfigurationKeyIsHonored() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(h -> true);
        session.setConfigProperty(MinioTransporterConfigurationKeys.CONFIG_PROP_FIXED_BUCKET_NAME, "custom-bucket");
        assertEquals("custom-bucket", effectiveBucket(session));
    }

    @Test
    void repositoryIdSuffixedKeyTakesPrecedence() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(h -> true);
        session.setConfigProperty(MinioTransporterConfigurationKeys.CONFIG_PROP_FIXED_BUCKET_NAME, "custom-bucket");
        session.setConfigProperty(
                MinioTransporterConfigurationKeys.CONFIG_PROP_FIXED_BUCKET_NAME + "." + repository.getId(),
                "repo-bucket");
        assertEquals("repo-bucket", effectiveBucket(session));
    }
}
