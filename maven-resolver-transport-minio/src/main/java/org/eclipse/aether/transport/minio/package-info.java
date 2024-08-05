// CHECKSTYLE_OFF: RegexpHeader
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
/**
 * Support for downloads/uploads via the S3 protocol. The implementation is backed by
 * <a href="https://github.com/minio/minio-java">MinIO Java</a>.
 * The repository URL should be defined with protocol {@code minio+http} or {@code s3+http}. Note: use "https" if
 * you are going for HTTPS remote, factory will merely strip "minio+" or "s3+" prefix assuming resulting URL will
 * point to expected S3 endpoint.
 *
 * @since 2.0.0
 */
package org.eclipse.aether.transport.minio;
