# Expected Checksums
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

Resolver uses checksums during transport to verify that the artifact is intact. The latest Resolver also uses checksums in other ways. For example, it verifies that the artifact is intact during resolution.

All checksum uses in Resolver have one purpose. That purpose is integrity verification. Resolver calculates the "calculated" checksum for a payload. Then it gets the "expected" checksum for the same payload. Then it compares the two checksums.

Resolver calculates a checksum for the bytes of an artifact using a standard algorithm. It can read the bytes and calculate the checksum during the download or from a file already present on the local file system. The calculation method depends on the transport.

This page covers all the "expected" checksum varieties.


## Transport Checksum Strategies

In the past, Resolver got the expected checksum with a request to the artifact checksum URL. The artifact checksum URL is the artifact URL with ".sha1" appended. This logic is still present in the current Resolver. The current Resolver extends this logic in several ways.

Resolver added two new strategies to the "obtain checksum" step. The three expected checksum kinds in transport are "Provided", "Remote Included", and "Remote External". All the strategies provide the source of the expected checksum. The strategies differ in **how** Resolver gets the checksum.

The **Provided** checksums come to Resolver by some alternative means. The checksums can arrive before any transport operation. Users can implement an SPI extension point. The extension point gives users their own way to provide checksums.

Users can also use the Resolver implementation that comes with Resolver. The implementation delegates the provided checksums to the trusted checksums. The section "Trusted Checksums" describes them.

The remote party includes the **Remote Included** checksums in its response. Most modern repository managers send checksums in the response headers. The checksums are usually the standard SHA-1 and MD5. Maven Central sends them as well. The Google Mirror of Maven Central sends them too.

Resolver extracts the checksums from the response. Then it gets the hashes that the remote repository provided with the content. This saves one HTTP round-trip. Resolver gets the content and the checksums in one response.

The **Remote External** checksums are the classic checksums. The remote repository stores them next to the artifact files. The layout of the remote repository defines the storage position. To get a Remote External checksum, send a new HTTP request to the remote repository. The order of the requested checksums follows the order in the layout configuration. Resolver asks for the checksums in the same order as the parameter contains the algorithm names.

During one artifact retrieval, Resolver runs the strategies in the order above. If the current strategy has no answer, Resolver tries the next strategy. If Resolver gets the expected checksum from the Provided source, it does not consult the Remote Included and Remote External sources.

Almost all repository managers and remote repositories send the standard checksums in their response. These repositories include Maven Central and the Google Mirror of Maven Central. If any standard checksum is enabled, the Remote Included strategy usually satisfies the verification. Then Resolver skips the Remote External strategy.

If Resolver gets the hashes with the Remote Included strategy, it halves the number of HTTP requests to download an artifact.

Related configuration keys:
* `aether.layout.maven2.checksumAlgorithms` A comma-separated list of checksum algorithms. The order is important. The transport asks for the checksums in the specified order. The default is "SHA-1,MD5". The first received and matched checksum stops the integrity verification algorithm.

Note: Since Maven 3.9.x you can use the expression `${session.rootDirectory}/.mvn/checksums/` to store checksums along with sources. `session.rootDirectory` becomes an absolute path. The path points to the root directory of your project. The `.mvn` directory is usually in the root directory.


### Provided Checksums

The Resolver SPI `ProvidedChecksumsSource` feeds the Provided Checksums to Resolver before the actual transport. Resolver uses these checksums only during transport. It uses them to verify the integrity of the transported payload. The Provided checksums cannot verify the integrity of cached artifacts. If you build with an empty repository, all your artifacts go through transport. Then the Provided checksums can verify them.

Resolver provides one SPI implementation with the distribution. The implementation delegates to the trusted checksums.

### Remote Included Checksums

**Note:** Remote Included checksums work only with transport-http. They do not work with transport-wagon.

Many repository services emit the reference checksums in the artifact response. Maven Central is one of these services. The service emits the checksums as HTTP headers. The Remote Included feature halves the number of HTTP requests. Resolver gets the artifact and the expected checksum with one HTTP round-trip.

Related configuration keys:
* `aether.connector.basic.smartChecksums` to enable or disable Remote Included checksums.

The Remote Included checksums support several "strategies" to extract checksums from the HTTP response header.


#### Sonatype Nexus 2

Sonatype Nexus 2 generates the `ETag` header from a SHA-1 hash. It shields the hash in the Plexus Cipher style. Only SHA-1 is available in the artifact response header.

Emitted by: Sonatype Nexus2 only.


#### Non-standard `X-` headers

Maven Central emits the headers `x-checksum-sha1` and `x-checksum-md5` along with the artifact response. Google GCS uses the headers `x-goog-meta-checksum-sha1` and `x-goog-meta-checksum-md5`. AWS S3 uses the headers `x-amz-meta-checksum-sha1` and `x-amz-meta-checksum-md5`. Resolver detects all these headers and uses their values.

Emitted by: Maven Central, GCS, AWS S3, some CDNs and probably more.


### Remote External checksums

The Remote External checksums are the classic checksums. They exist since Maven 1. The remote repository stores them next to the payload file. For example, it stores "lib.jar" and the checksum "lib.jar.sha1". They are the oldest kind of Resolver checksums.

Their shortcoming is that only SHA-1 and MD5 are often produced. The consumer is tied to the checksum algorithms that the remote repository provides. The payload and the checksum come from the same origin. If the origin is not trusted, this can be a risk. Maven Central is an example of a trusted origin.


## Trusted Checksums

All the expected checksums above are used only in transport. They relate to URLs, HTTP requests, and HTTP responses. Or they require transport related API elements.

`TrustedChecksumsSource` is an SPI component. It delivers the expected checksums for an artifact. It does not use any transport API element. This API is not bound to transport. It is generic.

The trusted checksums map almost one-to-one into the Provided strategy. Resolver provides an implementation that delegates the Provided checksums to the Trusted checksums. The Provided and Trusted checksums become equivalent for transport.

The biggest advantage of Trusted Checksums is their transport independence. They work in places where there is no transport. One use of Trusted Checksums is the ArtifactResolver post-processing.

This functionality verifies all the resolved artifacts against the Trusted Checksums. It costs checksum calculation overhead. The user provides a known checksum. The checksum can be cryptographically strong.

The functionality can help when the user cannot trust the local repository. An unknown or untrusted party can share the local repository.

You can record the checksums with the Trusted Checksum post-processor. For example, run it in a known and safe environment. Then distribute the produced checksum within your organization.

The Trusted Checksums distribution provides two source implementations.

Related configuration keys:
* `aether.trustedChecksumsSource.*`
* `aether.artifactResolver.postProcessor.trustedChecksums.*`

### Summary File Trusted Checksums Source

The summary file source uses one file. The file is compatible with the GNU coreutils format. Each line contains the hash and the relative path of an artifact. The path is relative to the local repository basedir.

You can produce the file with the `sha1sum` command line tool. GNU coreutils provides this tool. You can use the same tools to verify the artifacts in the local repository in a batch.

Each summary file contains information for one checksum algorithm. The file extension represents the algorithm.

If you use Maven 3.9.x, use the following procedure to save the summary checksum file alongside your project code:

1. Add the following command line flags to your `.mvn/config` file:

   ```
   --strict-checksums
   -Daether.checksums.algorithms=SHA-512,SHA-1,MD5
   -Daether.trustedChecksumsSource.summaryFile=true
   -Daether.trustedChecksumsSource.summaryFile.basedir=${session.rootDirectory}/.mvn/checksums/
   -Daether.artifactResolver.postProcessor.trustedChecksums=true
   -Daether.artifactResolver.postProcessor.trustedChecksums.checksumAlgorithms=SHA-512
   -Daether.artifactResolver.postProcessor.trustedChecksums.failIfMissing=true
   ```

2. Run a build with trusted checksum recording enabled:

   ```sh
   mvn clean install -Daether.artifactResolver.postProcessor.trustedChecksums.record=true
   ```

   The build generates one or more checksum files with the `.sha512` extension. Each source Maven repository generates one file.

3. Verify that the build succeeds with trusted checksum recording disabled:

   ```sh
   mvn clean install
   ```

### Sparse Directory Trusted Checksums Source

This source mimics the Maven local repository layout. It stores the checksums in a similar layout.

The sparse directory can contain checksums for multiple algorithms. The file extension encodes the algorithm.

### Notes On Using Trusted Checksums

- Use the `--strict-checksums` flag to fail a build if the expected checksums of the downloaded artifacts do not match.
- You can specify more than one checksum algorithm for the `aether.artifactResolver.postProcessor.trustedChecksums.checksumAlgorithms` system property. The listed checksums must be a subset of the checksums in `aether.checksums.algorithms`.
- Most dependency management tools do not update trusted checksum files. This is true if the files are stored in version control alongside the source code. We hope that the maintainers of these tools support Maven trusted checksums in the near future.
