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

Checksums in Resolver were historically used during transport, 
to ensure Artifact integrity. In addition, latest Resolver may 
use checksums in various other ways too, for example to ensure 
Artifact integrity during resolution. 

The bare essence of all checksum uses in Resolver is 
"integrity validation": Resolver calculates by various
means the "calculated" checksum (for given payload), 
then obtains somehow the "expected" checksum (for same payload)
and compares the two.

The "calculated" checksum is uninteresting from technical viewpoint,
as it is calculated by standard means: either during payload
streaming, or in worst case, from file already present on local
file system (transport dependant).

Instead, this page covers all the "expected" checksum varieties.


## Transport Checksum Strategies

Historically, the "obtain expected checksum" was implemented as simple 
request against Artifact checksum URL (Artifact URL appended by ".sha1"). This logic 
is still present in current Resolver, but is "decorated" and extended in multiple 
ways.

Resolver has broadened the "obtain checksum" step for "expected" checksum with two new strategies,
so the three expected checksum kinds in transport are: "Provided", "Remote Included" and 
"Remote External". All these strategies provide the source of "expected" checksum, 
but it differs **how** Resolver obtains these.

The new **Provided** kind of expected checksums are provided to resolver by some alternative
means, possibly ahead of any transport operation. There is an SPI extension point that users may 
implement, to have own ways to provide checksums to resolver. Alternatively, one may use Resolver out of the 
box implementation, that simply delegates "provided checksums" to "trusted checksums" (more about them later).

The new **Remote Included** checksums are in some way included by remote party, typically 
in their response. Since advent of modern Repository Managers, most of 
them already sends checksums (usually the "standard" SHA-1 and MD5)
in their response headers. Moreover, Maven Central, and even Google Mirror of Maven Central 
sends them as well. By extracting these checksums from response, we can get hashes
that were provided by remote repository along with its content. This saves one HTTP round-trip, as we
got both, content and checksums in one response.

Finally, the **Remote External** checksums are the "classic" checksums we all know: They are laid down 
next to Artifact files, external in other words on the remote repository, according 
to remote repository layout. To obtain Remote External checksum, new HTTP request against remote repository is
required. The order of requested checksums will follow the order given in layout configuration, 
asking for checksums in same order as the parameter contains algorithm names.

During single artifact retrieval, these strategies are executed in above specified order,
and only if current strategy has "no answer", the next strategy is attempted. Hence, if 
resolver is able to get "expected" checksum from Provided Checksum Source, the Remote Included
and Remote External sources will not be consulted. Important implication: given that almost
all MRMs and remote repositories (Maven Central, Google Mirror of Maven Central) send "standard" (SHA-1, MD5)
checksums in their response, if any of the standard checksum are enabled, validation will
be probably satisfied by "Remote Included" strategy and "Remote External" will be skipped. 

The big win here is that by obtaining hashes using "Remote Included" and not by "Remote External"
strategy, we can halve the count of HTTP requests to download an Artifact.

Related configuration keys:
* `aether.layout.maven2.checksumAlgorithms` A comma-separated list of checksum algorithms. Order is important, as
  transport will ask for those in specified order (default is "SHA-1,MD5"), and first received and matched causes
  integrity validation algorithm to stop.

Note: Since Maven 3.9.x you can use expression `${session.rootDirectory}` to store checksums along with
sources as `session.rootDirectory` will become an absolute path pointing to the root directory of your project (where
usually the `.mvn` directory is).


### Provided Checksums

There is a Resolver SPI `ProvidedChecksumsSource` that makes possible to feed Provided Checksums to Resolver ahead
of actual transport. These checksums are used **during transport only** to verify transported payload (artifacts) 
integrity. Hence, Provided checksums are NOT usable to verify already cached artifacts integrity (unless you build
with empty repository, of course, that forces all of your artifact go through transport).

Resolver out of the box provides one SPI implementation: one that simply delegates to "trusted checksums".

### Remote Included Checksums

**Note: Remote Included checksums work only with transport-http, they do NOT work with transport-wagon!**

By using "Remote Included" checksum feature, we are able to halve the issued HTTP request 
count, since many repository services along Maven Central emits the reference checksums in
the artifact response itself (as HTTP headers). Hence, we are able to get the
artifact and reference "expected" checksum using only one HTTP round-trip.

Following sources are supported:

Related configuration keys:
* `aether.connector.basic.smartChecksums` to enable or disable Remote Included checksums.

#### Sonatype Nexus 2

Sonatype Nexus 2 uses SHA-1 hash to generate `ETag` header in "shielded" (à la Plexus Cipher)
way. Naturally, this means only SHA-1 is available in artifact response header.

Emitted by: Sonatype Nexus2 only.


#### Non-standard `X-` headers

Maven Central emits headers `x-checksum-sha1` and `x-checksum-md5` along with artifact response. 
Google GCS on the other hand uses `x-goog-meta-checksum-sha1` and `x-goog-meta-checksum-md5` 
headers. Resolver will detect these and use their value.

Emitted by: Maven Central, GCS, some CDNs and probably more.


### Remote External checksums

These are the "classic" checksums existing since Maven 1. They are laid on layout in the remote repository, next
to the payload file (i.e. "lib.jar" and checksum "lib.jar.sha1"). While they are the oldest kind of Resolver checksums,
their shortcoming is that most often only SHA-1 and MD5 are produced. Basically, consumer is tied to those checksum
algorithms only, that are provided by remote repository. Similarly, given both, the payload and the checksum comes
from same origin, unless the origin is trusted (like Maven Central is), it may be seen as a risk.


## Trusted Checksums

All the "expected" checksums discussed above are used in transport only, they are all
about URLs, HTTP requests and responses, or require Transport related API elements.

`TrustedChecksumsSource` is a SPI component that is able to deliver "expected" checksums 
for given Artifact, without use of any transport API element. In other words, this
API is not bound to transport, but is generic.

Since they map almost one-to-one into transport "Provided Checksum" strategy, resolver provides 
implementation that delegates Provided to Trusted checksums (makes Provided and Trusted 
checksums equivalent, transport-wise).

But the biggest game changer of Trusted Checksums is their transport independence, that they
can be utilized in places where there is no transport happening at all.  One of such uses of 
Trusted Checksums is ArtifactResolver post-processing.
This new functionality, at the cost of checksum calculation overhead, is able to validate all
the resolved artifacts against Trusted Checksums, thus, making sure that all resolved
artifacts are "validated" with some known (possibly even cryptographically strong) checksum
provided by user. This new feature may become handy in cases when user cannot trust the local
repository, as it may be shared with some other unknown or even untrusted party.

Moreover, using Resolver Trusted Checksum post-processor, one can "record" the checksums,
for example when executed in a known "pristine" and safe environment, and reuse the produced
checksum to distribute within organization.

The Trusted Checksums provide two source implementations out of the box.

Related configuration keys:
* `aether.trustedChecksumsSource.*`
* `aether.artifactResolver.postProcessor.trustedChecksums.*`

### Summary File Trusted Checksums Source

The summary file source uses single file that is in GNU coreutils compatible format: each
line contains the hash and relative path of artifact from local repository basedir.

The file can be produced using common OS and GNU coreutils `sha1sum` command line tools,
and the same tools can be also used to "batch verify" the enlisted artifacts in local repository.

Each summary file contains information for single checksum algorithm, represented as summary file extension.

### Sparse Directory Trusted Checksums Source

This source mimics Maven local repository layout, and stores checksums in similar layout
as Maven local repository stores checksums in local repository.

Hare, just like Maven local repository, the sparse directory can contain multiple algorithm checksums,
as they are coded in checksum file path (the extension).

