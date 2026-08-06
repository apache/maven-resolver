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

Resolver uses checksums during transport to verify that the artifact is intact. The latest Resolver also uses checksums at other times. For example, it verifies that the artifact is intact during resolution.

Checksums in Resolver provide integrity verification. Resolver determines the "calculated" checksum for an artifact by applying a mathematical algorithm to its local copy of the artifact. It can read the bytes and calculate the checksum during the download or from a file already present on the local file system. Then it retrieves the "expected" checksum for the same artifact. It compares the two checksums to see if the artifact is intact or corrupt.

This page covers the different ways Resolver can retrieve the "expected" checksum for an artifact that it compares against the locally calculated checksum.


## Transport Checksum Strategies

Resolver has three strategies for retrieving the expected checksum: "Provided", "Remote Included", and "Remote External". Appending a checksum extension to the artifact URL is an example of the "Remote External" strategy. 
The strategies differ in **how** Resolver gets the checksum.

**Provided** checksums are supplied to the Resolver through the Resolver's Java API. Users can implement or install an SPI extension point that loads checksums. The checksums can load before any transport operation. 
Users can also use the trusted checksum SPI bundled with the Resolver implementation. The section "Trusted Checksums" below describes this approach.

**Remote Included** checksums are part of the response that the artifact itself arrives in. Most modern repository managers send checksums in the HTTP response headers. Maven Central and the Google Mirror of Maven Central send the SHA-1 checksum as a hexadecimal string in the X-Checksum-Sha1 HTTP header.
They send the hexadecimal encoded MD5 checksum in the X-Checksum-Md5 HTTP header.
Resolver gets the content and the checksums in one HTTP request.

**Remote External** checksums are separate resources in the remote repository. The remote repository stores them next to the artifact files. To get a Remote External checksum, Resolver sends a new HTTP GET request for the artifact checksum URL.  This is the artifact URL with an algorithm extension such as ".sha1" appended. For example, if the artifact URL is https://repo1.maven.org/maven2/xom/xom/1.3.9/xom-1.3.9.jar, then the SHA-1 checksum URL is https://repo1.maven.org/maven2/xom/xom/1.3.9/xom-1.3.9.jar.sha1, and the MD5 checksum URL is https://repo1.maven.org/maven2/xom/xom/1.3.9/xom-1.3.9.jar.md5.


When retrieving an artifact, Resolver runs the strategies in this order until it finds a checksum:

1. Provided
2. Remote Included
3. Remote External

If Resolver gets the expected checksum from the Provided source, it does not consult the Remote Included and Remote External sources.

Almost all repository managers and remote repositories send standard checksums in their responses.
If any standard checksum algorithm is enabled, the Remote Included strategy usually finds a checksum. Then Resolver skips the Remote External strategy.

Related configuration keys:
* `aether.layout.maven2.checksumAlgorithms` A comma-separated list of checksum algorithms. The order is important. The transport asks for the checksums in the specified order. The default is "SHA-1,MD5". The first available algorithm will be used. For example, if you prefer MD5 but are willing to use SHA-1, set `aether.layout.maven2.checksumAlgorithms` to "MD5,SHA-1".

In Maven 3.9.x and later, you can use the expression `${session.rootDirectory}/.mvn/checksums/` to store checksums alongside sources. `session.rootDirectory` becomes an absolute path. The path points to the root directory of your project. The `.mvn` directory is usually in the root directory.


### Provided Checksums

The Resolver SPI `ProvidedChecksumsSource` feeds the Provided Checksums to Resolver before the actual transport. Resolver uses these checksums during transport to verify the integrity of the transported payload. Provided checksums cannot verify the integrity of cached artifacts. If you build with an empty repository, all your artifacts go through transport. Then the Provided checksums can verify them.

Resolver provides one SPI implementation with the distribution. This implementation delegates to the trusted checksums.

### Remote Included Checksums

**Note:** Remote Included checksums only work with transport-http. They do not work with transport-wagon.

Many repository services include the reference checksums in the HTTP response headers. Maven Central is one of these services. Resolver gets the artifact and the expected checksum with one HTTP round-trip.

Related configuration keys:
* `aether.connector.basic.smartChecksums` to enable or disable Remote Included checksums.


Resolver checks several non-standard `X-` headers for checksums:

* `x-checksum-sha1` and `x-checksum-md5`: Maven Central and the Google Mirror of Maven Central
* `x-goog-meta-checksum-sha1` and `x-goog-meta-checksum-md5`: Google Cloud Storage
* `x-amz-meta-checksum-sha1` and `x-amz-meta-checksum-md5`:  AWS S3

Resolver detects all these headers and uses their values. You don't need to tell it in advance which variant to expect.


### Remote External checksums

The Remote External checksums are the classic checksums. The remote repository stores them next to the payload file. For example, it stores "lib.jar" and the checksum "lib.jar.sha1" in the same directory. They are the oldest kind of Resolver checksums.


## Trusted Checksums

All the expected checksums above are used only in transport. They relate to URLs, HTTP requests, and HTTP responses. Or they require transport related API elements.

`TrustedChecksumsSource` is an SPI component. It delivers the expected checksums for an artifact. It does not use any transport API element. This API is not bound to transport. It is generic.

Trusted checksums map almost one-to-one into the Provided strategy. Resolver provides an implementation that delegates the Provided checksums to the Trusted checksums. The Provided and Trusted checksums become equivalent for transport.

Transport independence is the biggest advantage of Trusted Checksums. They work in places where there is no transport such as ArtifactResolver post-processing. This functionality verifies all the resolved artifacts against the Trusted Checksums. The user provides a known checksum that can be cryptographically strong.
This helps when the user cannot trust the local repository because an unknown or untrusted party shares the local repository.

You can record the checksums with the Trusted Checksum post-processor. For example, run it in a known and safe environment. Then distribute the produced checksum within your organization.

The Trusted Checksums distribution provides two source implementations.

Related configuration keys:
* `aether.trustedChecksumsSource.*`
* `aether.artifactResolver.postProcessor.trustedChecksums.*`

### Summary File Trusted Checksums Source

The summary file source uses one file. The file is compatible with the GNU coreutils format. Each line contains the hash and the relative path of an artifact. The path is relative to the local repository basedir.

You can produce the file with the `sha1sum` command line tool from GNU coreutils. You can use the same tool to verify the artifacts in the local repository.

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

- Use the `--strict-checksums` flag to fail a build if the expected checksum of a downloaded artifacts does not match the trusted checksum.
- You can specify more than one checksum algorithm for the `aether.artifactResolver.postProcessor.trustedChecksums.checksumAlgorithms` system property. The listed checksums must be a subset of the checksums in `aether.checksums.algorithms`.
- Most dependency management tools do not update trusted checksum files. This is true if the files are stored in version control alongside the source code. We hope that these tools will support Maven trusted checksums in the near future.
