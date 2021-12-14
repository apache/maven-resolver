# Included Checksum Strategies
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

**Note: these below works only with transport-http, does NOT work with transport-wagon!**

By default, resolver will fetch the payload checksum from remote repository. These
checksums are used to enforce transport validity (ensure that download was not 
corrupted during transfer).

This implies, that to get one artifact or metadata, resolver 
needs to issue two HTTP requests: one to get the payload itself, and one to 
get the reference checksum.

By using "included checksums" feature, we are able to halve the issued HTTP request 
count, as many services along Maven Central emits the reference checksums in
the artifact response itself (as HTTP headers), hence, we are able to get the
artifact and reference checksum using only one HTTP round-trip.


## Sonatype Nexus 2

Sonatype Nexus 2 uses SHA-1 hash to generate `ETag` header in "shielded" (à la Plexus Cipher)
way. Naturally, this means only SHA-1 is available in artifact response header.

Emitted by: Sonatype Nexus2 only.


## Non-standard `X-` headers

Maven Central emits headers `x-checksum-sha1` and `x-checksum-md5` along with artifact response. 
Google GCS on the other hand uses `x-goog-meta-checksum-sha1` and `x-goog-meta-checksum-md5` 
headers. Resolver will detect these and use their value.

Emitted by: Maven Central, GCS, some CDNs and probably more.
