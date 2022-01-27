# About Checksums
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

Maven Resolver uses checksums to verify the consistency of the downloaded 
artifacts. Checksums are usually laid out in repositories next to the file in question, with file 
extension telling the checksum algorithm that produced the given checksum file content. Current Maven Repository 
layout contains SHA-1 and MD5 checksums by default (they are produced by Resolver by default).

Historically, Maven Resolver used `java.security.MessageDigest` to implement checksums. So to say, secure one-way
hashes provided by Java Cryptography Architecture were (mis)used to implement checksums for transport integrity 
validation. There is no misunderstanding here, secure hashes MAY be used as checksums, as there is quite some 
overlap between "checksums" and "hashes" in general. But this simplicity comes at a price: cryptographically "safe" 
algorithms require way more CPU cycles to calculate checksum, while all their purpose is just 
"integrity validation", nothing more. There is no "security", "trust" or whatever else implied or expected from
them.

If you are interested in "trust" in your artifacts, it is Artifact Signatures (for example
[GPG Signatures](https://maven.apache.org/plugins/maven-gpg-plugin/)) that you should look for.

Hence, the usual argument that "XXX algorithm is unsafe, deprecated, not secure anymore" does not stand in use case
of Maven Resolver: there is nothing "secure" being involved with checksums. Moreover, this is true not only for SHA-1
algorithm, but even for its "elder brother" MD5. Both algorithms are still widely used today as "transport integrity
validation" or "error detection" (aka "bit-rot detection").

## Checksum Changes

From technical perspective, the above written facts infer following consequences: as checksum algorithms are exposed 
for user, so one can set them via configuration, users are not prevented to ask for SHA-256 or even SHA-512, even if
these algorithms are not part of "standard" Maven repository layout. Moreover, nothing prevent users (integrating
Maven Resolver) registering with Java an alternate Java Cryptography Provider and use even broader (or exotic) set 
of message digests for checksums. While this is not "wrong" or even "mistake" in any case, we do consider this as 
wrong use case. The notion of "transport checksums" and "secure hashes" are being constantly mixed up due historical
reasons explained above.

Hence, Maven Resolver team decided to make supported set of checksums limited. Instead of directly exposing
`MessageDigest` algorithms, we introduced API around checksums. This not only prevents "wrong use cases" (not
exposing all supported algorithms of `MessageDigest` to users), but also makes possible to introduce new modern 
(and fast) real checksum algorithms like xxHash is. Finally, the set of supported checksum algorithms remains 
extensible: if some required algorithm is not provided by resolver, it can be easily added by creating a factory
component for it.

Resolver out of the box supported checksum algorithms are:

* MD5
* SHA-1
* SHA-256
* SHA-512

We are aware that users started using "better SHA" algorithms, and we do not want to break them. For them nothing
changes (configuration and everything basically remains the same). But, we do want to prevent any possible further 
proliferation of non-standard checksums.

Links:
* [SHA-1](https://en.wikipedia.org/wiki/SHA-1) (see "Data Integrity" section)
* [MD5](https://en.wikipedia.org/wiki/MD5) (see "Applications" section, especially about error checking functionality)