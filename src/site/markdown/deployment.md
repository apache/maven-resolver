# Deployment
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

Deploying artifacts and related metadata to a (remote) repository can be achieved via Resolver API with method [`org.eclipse.aether.RepositorySystem.deploy(RepositorySystemSession session, DeployRequest request)`](https://github.com/apache/maven-resolver/blob/master/maven-resolver-api/src/main/java/org/eclipse/aether/RepositorySystem.java). This writes/uploads the given artifact(s) including metadata to the given repository leveraging a [`RepositoryConnector`](https://github.com/apache/maven-resolver/blob/master/maven-resolver-spi/src/main/java/org/eclipse/aether/spi/connector/RepositoryConnector.java).

The most prominent consumer of this API is probably [maven-deploy-plugin](https://maven.apache.org/plugins/maven-deploy-plugin/).

# Repository Connector

The default repository connector implementation at [`BasicRepositoryConnectorFactory`](https://github.com/apache/maven-resolver/blob/master/maven-resolver-connector-basic/src/main/java/org/eclipse/aether/connector/basic/BasicRepositoryConnector.java) uses a `RepositoryLayout` to calculate the URL and a `Transporter` to achieve the actual upload/write of artifacts/metadata.

## Repository Layout

The repository layout determines the location to which the artifact is being written/uploaded with its `RepositoryLayout.getLocation(Artifact, true)` or `RepositoryLayout.getLocation(Metadata, true)` method. For [Maven 2 repositories](https://maven.apache.org/repositories/layout.html) the logic is implemented in [`Maven2RepositoryLayoutFactory`](https://github.com/apache/maven-resolver/blob/master/maven-resolver-impl/src/main/java/org/eclipse/aether/internal/impl/Maven2RepositoryLayoutFactory.java).

## Transporter

All transporter implementations have a [`put(...)`](https://github.com/apache/maven-resolver/blob/master/maven-resolver-spi/src/main/java/org/eclipse/aether/spi/connector/transport/Transporter.java) method which is called during deployment. The repository's URL protocol determines which method is used for the deployment. The standard transporters implement `put(...)` like follows:

URL Protocol | Implementation | Description
 --- | --- | ---
`file`, `bundle` | `org.eclipse.aether.transport.file.FileTransporter` | Writes artifact/metadata to the file system.
`http`, `https` | multiple | Issues a HTTP PUT request for each given artifact/metadata.
`classpath` | `org.eclipse.aether.transport.classpath.ClasspathTransporter` | Unsupported
`minio+http`, `minio+https`, `s3+http`, `s3+https` | `org.eclipse.aether.transport.minio.MinioTransporter` | Uploads artifact/metadata as object to bucket. The location returned from the `RepositoryLayout` is being converted to an object and bucket name according to the configuration.
`*` | `org.eclipse.aether.transport.wagon.WagonTransporter` | Calls `StreamingWagon.putFromStream(...)` or `Wagon.put(...)`. See [Apache Wagon](https://maven.apache.org/wagon/) for further details.

