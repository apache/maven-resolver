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

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Map;

import io.minio.MinioClient;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transport.minio.MinioTransporterConfigurationKeys;
import org.eclipse.aether.transport.minio.ObjectName;
import org.eclipse.aether.transport.minio.ObjectNameMapper;
import org.eclipse.aether.transport.minio.ObjectNameMapperFactory;
import org.eclipse.aether.util.ConfigUtils;

/**
 * A fixed bucket mapper, uses given bucket ID and then constructs object name using repository ID and layout path as
 * object name.
 */
@Singleton
@Named(FixedBucketObjectNameMapperFactory.NAME)
public class FixedBucketObjectNameMapperFactory implements ObjectNameMapperFactory {
    public static final String NAME = "fixedBucket";

    @Override
    public ObjectNameMapper create(
            RepositorySystemSession session,
            RemoteRepository repository,
            MinioClient unused,
            Map<String, String> headers) {
        String bucket = ConfigUtils.getString(
                session,
                MinioTransporterConfigurationKeys.CONFIG_PROP_FIXED_BUCKET_NAME,
                MinioTransporterConfigurationKeys.CONFIG_PROP_FIXED_BUCKET_NAME + "." + repository.getId(),
                MinioTransporterConfigurationKeys.DEFAULT_FIXED_BUCKET_NAME);
        return path -> new ObjectName(bucket, repository.getId() + "/" + ObjectName.normalize(path));
    }
}
