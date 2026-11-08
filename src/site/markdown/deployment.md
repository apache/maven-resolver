# Deployment
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

You can deploy artifacts and related metadata to a (remote) repository with the Resolver API method [`org.eclipse.aether.RepositorySystem.deploy(RepositorySystemSession session, DeployRequest request)`](https://github.com/apache/maven-resolver/blob/master/maven-resolver-api/src/main/java/org/eclipse/aether/RepositorySystem.java).
The method writes or uploads them to the repository through a [`RepositoryConnector`](https://github.com/apache/maven-resolver/blob/master/maven-resolver-spi/src/main/java/org/eclipse/aether/spi/connector/RepositoryConnector.java).

The main consumer of this API is [maven-deploy-plugin](https://maven.apache.org/plugins/maven-deploy-plugin/).

## Repository Connector

The default implementation of the repository connector is [`BasicRepositoryConnector`](https://github.com/apache/maven-resolver/blob/master/maven-resolver-connector-basic/src/main/java/org/eclipse/aether/connector/basic/BasicRepositoryConnector.java).
It uses a `RepositoryLayout` to calculate the URL.
It uses a `Transporter` to write or upload the artifacts and metadata.

## Repository Layout

The repository layout determines the location where the artifact will be written or uploaded.
It uses the `RepositoryLayout.getLocation(Artifact, true)` or `RepositoryLayout.getLocation(Metadata, true)` method for this.
For [Maven 2 repositories](https://maven.apache.org/repositories/layout.html), [`Maven2RepositoryLayoutFactory`](https://github.com/apache/maven-resolver/blob/master/maven-resolver-impl/src/main/java/org/eclipse/aether/internal/impl/Maven2RepositoryLayoutFactory.java) implements the logic.

## Transporter

All transporter implementations have a [`put(...)`](https://github.com/apache/maven-resolver/blob/master/maven-resolver-spi/src/main/java/org/eclipse/aether/spi/connector/transport/Transporter.java) method.
Resolver calls the method during deployment.
The URL protocol of the repository determines which method Resolver uses for the deployment.
The standard transporters implement `put(...)` as follows:

URL Protocol | Implementation | Description
 --- | --- | ---
`file`, `bundle` | `org.eclipse.aether.transport.file.FileTransporter` | Writes the artifact or metadata to the file system.
`http`, `https` | multiple | Issues an HTTP PUT request for each artifact or metadata.
`classpath` | `org.eclipse.aether.transport.classpath.ClasspathTransporter` | Unsupported
`minio+http`, `minio+https`, `s3+http`, `s3+https` | `org.eclipse.aether.transport.minio.MinioTransporter` | Uploads the artifact or metadata as an object to the bucket. The configuration defines how the transporter converts the location from the `RepositoryLayout` into an object name and a bucket name.
`*` | `org.eclipse.aether.transport.wagon.WagonTransporter` | Calls `StreamingWagon.putFromStream(...)` or `Wagon.put(...)`. [Apache Wagon](https://maven.apache.org/wagon/) provides further details.

### S3 and S3-compatible object stores over the HTTP transports

With path-style access an S3 bucket is an HTTP repository: the repository URL is
`https://<endpoint>/<bucket>/<prefix>/`, and the only addition S3 needs is a signature on each request.
Set `aether.connector.http.requestSigner` to `aws-sigv4`, for one repository with the `.<repositoryId>`
suffix, and the Apache, JDK and Jetty transports sign every request with AWS Signature Version 4. The
repository username and password are the access key and the secret key; without repository authentication
the `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` and `AWS_SESSION_TOKEN` environment variables are used.
The region comes from `aether.connector.http.aws.region`, then `AWS_REGION` or `AWS_DEFAULT_REGION`, then
`us-east-1`, which S3-compatible stores accept. The signature never leaves the repository origin: a redirect
to another host is followed without it. The MinIO transporter above offers the same through dedicated
`minio+https` and `s3+https` URLs and an SDK client.
