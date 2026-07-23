# Checksums
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

Maven Resolver uses checksums to verify the integrity of downloaded artifacts and
metadata. Checksums are usually placed in repositories next to the file in question, with the file
extension indicating the checksum algorithm that produced the given file. Currently,
most Maven repositories contain SHA-1 and MD5 checksums as they are produced by Resolver by default).

Historically, Maven Resolver used `java.security.MessageDigest` to implement checksums. Secure one-way
hashes provided by the Java Cryptography Architecture were (mis)used to implement checksums for transport integrity
validation. Secure hashes MAY be used as checksums, as there is quite some
overlap between checksums and hashes in general. But this simplicity comes at a price: cryptographically safe
algorithms require way more CPU cycles to compute than a simple checksum. However, the purpose of a checksum is just
integrity validation, nothing more. There is no security or trust implied or expected from
them. Checksums do not protect against man-in-the-middle or supply chain attacks.

To actually trust that artifacts have not been tampered with, you need signatures such
those provided by the 
[Maven GPG Plugin](https://maven.apache.org/plugins/maven-gpg-plugin/)).

Hence, the usual argument that "XXX algorithm is unsafe, deprecated, not secure anymore" does not apply in the case
of Maven Resolver. Moreover, this is true not only for SHA-1
algorithm, but even for its "elder brother" MD5. A checksum is not intended to be secure. Both algorithms are still widely used today as "transport integrity
validation" or "error detection" (a.k.a. "bit-rot detection").

## Checksum Algorithms SPI

From a technical perspective, the above facts imply the following consequences: because checksum algorithms are exposed
to the user, one can set them via configuration, and thus users are not prevented from asking for SHA-256 or even SHA-512, even if
these algorithms are not part of standard Maven process. Moreover, nothing prevents users (integrating
Maven Resolver) registering an alternate Java Cryptography Provider and using even broader (or exotic)
message digest algorithms for checksums. While this is not wrong, we do consider this as a
bad use case. The notion of transport validation and secure hashes are being constantly mixed up due to historical
reasons explained above.

Hence, the Maven Resolver team decided to make the supported set of checksum algorithms more controlled. Instead of directly exposing
`MessageDigest` algorithms, we introduced an SPI around checksums. This not only prevents incorrect use cases  by not
exposing all supported algorithms of `MessageDigest` to users, but also makes it possible to introduce real checksum
algorithms. Finally, the set of supported checksum algorithms remains extensible: if some required algorithm is
not provided by Resolver, it can easily be added by creating a factory component for it.

We are aware that users started using "better SHA" algorithms, and we do not want to break them. Nothing for them
changes (configuration and everything basically remains the same). But we do want to prevent any possible further
proliferation of non-standard checksums.

## Implemented Checksum Algorithms

Resolver out of the box provides the following checksum algorithms (important: algorithm names are case sensitive):

* MD5
* SHA-1
* SHA-256
* SHA-512

The algorithms above are provided by Resolver, by default. Still, using the SPI, anyone can extend
Resolver with new types of Checksum Algorithms.

To see how and when checksums are used in Resolver, continue on [Expected Checksums](expected-checksums.html)
page.

Links:

* [SHA-1](https://en.wikipedia.org/wiki/SHA-1) (see "Data Integrity" section)
* [MD5](https://en.wikipedia.org/wiki/MD5) (see "Applications" section, especially about error checking functionality)

