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
means, possibly ahead of any transport operation. There is an SPI interface that users may 
implement, to have own ways to provide checksums to resolver. Alternatively, one may use Resolver out of the 
box implementation, that simply delegates "provided checksums" to "trusted checksums" (more about them later).

The new **Remote Included** checksums are in some way included by remote party, typically 
in their response. Since advent of modern Repository Managers, most of 
them already sends checksums (usually the "standard" SHA-1 and MD5)
in their response headers. Moreover, Maven Central, and even Google Mirror of Maven Central 
sends them as well. By extracting these checksums from response, we can get hashes
that were provided by remote repository along with its content. 

Finally, the **Remote External** checksums are the classic checksums we all know: They are laid down 
next to Artifact files, external in other words on the remote repository, according 
to remote repository layout. To obtain Remote External checksum, new request again remote repository is
required. The order of requested checksums will follow the order given in `aether.checksums.algorithms`, 
it asks for checksums in same order as the parameter contains algorithm names.

During single artifact retrieval, these strategies are executed in above specified order,
and only if current strategy has "no answer", the next strategy is attempted. Hence, if 
resolver is able to get "expected" checksum from Provided Checksum Source, the Remote Included
and Remote External sources will not be consulted. Important implication: given that almost
all MRMs and remote repositories (Maven Central, Google Mirror of Maven Central) send "standard" (SHA-1, MD5)
checksums in their response, if any of the standard checksum are enabled, validation will
be probably satisfied by "Remote Included" strategy and "Remove External" will be skipped. 

The big win here is that by obtaining hashes using "Remote Included" and not by "Remote External"
strategy, we can halve the count of HTTP requests to download an Artifact.

### Remote Included Strategies

**Note: Remote Included checksums work only with transport-http, they do NOT work with transport-wagon!**

By using "Remote Included" checksum feature, we are able to halve the issued HTTP request 
count, since many repository services along Maven Central emits the reference checksums in
the artifact response itself (as HTTP headers). Hence, we are able to get the
artifact and reference "expected" checksum using only one HTTP round-trip.


#### Sonatype Nexus 2

Sonatype Nexus 2 uses SHA-1 hash to generate `ETag` header in "shielded" (à la Plexus Cipher)
way. Naturally, this means only SHA-1 is available in artifact response header.

Emitted by: Sonatype Nexus2 only.


#### Non-standard `X-` headers

Maven Central emits headers `x-checksum-sha1` and `x-checksum-md5` along with artifact response. 
Google GCS on the other hand uses `x-goog-meta-checksum-sha1` and `x-goog-meta-checksum-md5` 
headers. Resolver will detect these and use their value.

Emitted by: Maven Central, GCS, some CDNs and probably more.


## Trusted Checksums

All the "expected" checksums discussed above are trasport bound, they are all
about URLs, HTTP requests and responses, or require Transport related API elements.

Trusted checksums is a SPI component that is able to deliver "expected" checksums 
for given Artifact, without use of any transport API element. In other words, this
API is not bound to transport.

Since they almost on-to-one into transport "Provided Checksum" strategy, resolver provides 
implementation that simply delegates Provided to Trusted checksums (is able to
make Provided and Trusted checksums equivalent, as transport is regarded).

But the biggest game changer of Trusted Checksums is their transport independence, that they
can be utilized in places where there is no transport happening at all.

One of such uses of Trusted Checksums in Resolver is in ArtifactResolver post-processing.
This new functionality, at the cost of checksum calculation overhead, is able to validate all
the resolved artifacts against Trusted Checksums, thus, making sure that all resolved
artifacts are "validated" with some known (possibly even cryptographically strong) checksum.

This new post-processing may become handy in cases when user does not trust the local
repository, as may be shared with some other unknown or even untrusted party.

The Trusted Checksums provide two source implementations out of the box.

### Summary File Trusted Checksums Source

The summary file source uses single file that is in GNU coreutils compatible format: each
line contains the hash and relative path of artifact from local repository basedir.

The file can be produced using plain GNU coreutils `sha1sum` and alike command line tools,
and same tools can be also used to "batch verify" all the artifacts enlisted in the summary file.

Each file contains information for single checksum algorithm (noted by file extension).

### Sparse Directory Trusted Checksums Source

This source mimics Maven local repository layout, and stores checksums in similar layout
as Maven local repository stores checksums in local repository.

Hare, just like Maven local repository, the sparse directory can contain multiple algorithm checksums
as they are coded in looked up checksum path (the extension).
