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

/**
 * Support for downloads and uploads over the S3 API. The implementation is built on the
 * <a href="https://github.com/aws/aws-sdk-java-v2">AWS SDK for Java 2.x</a> with its JDK URL connection HTTP client,
 * uses path-style access, and does not send the CRC-based request checksums that S3-compatible stores commonly lack.
 * The repository URL should be defined with protocol {@code s3+https}; the factory merely strips the "s3+" prefix
 * assuming the resulting URL points to the S3 endpoint, with the bucket chosen by the configured object name mapper.
 * Credentials come from the repository authentication (username as access key, password as secret key) when set,
 * and from the SDK default credentials provider chain otherwise.
 * The plaintext {@code s3+http} form is refused by default: it offers no transport integrity and is not matched by
 * {@code external:http:*} mirror blocking. To use a plaintext endpoint anyway, explicitly set the configuration
 * property {@code aether.transport.s3.allowInsecureProtocol} to {@code true}.
 *
 * @since 2.0.26
 */
package org.eclipse.aether.transport.s3;
