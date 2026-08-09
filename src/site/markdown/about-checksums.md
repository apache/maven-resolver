# Checksums
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

Maven Resolver uses checksums to verify the integrity of downloaded artifacts and metadata.
Checksums exist in repositories next to the target file.
The file extension identifies the checksum algorithm that produced the checksum.
Most Maven repositories contain SHA-1 and MD5 checksums by default.
Maven Resolver also produces these checksums by default.
Checksums only provide integrity verification. They do not provide security or trust.
They do not protect against man-in-the-middle or supply chain attacks.

In the past, Maven Resolver used `java.security.MessageDigest` to calculate checksums.
The Java Cryptography Architecture provides secure one-way hashes.
Maven Resolver used these secure hashes to verify transport integrity.
Secure hashes work as checksums, but cryptographically safe algorithms
require many more CPU cycles to calculate than a simple checksum.

Some users state that specific algorithms are unsafe or deprecated.
This argument does not apply to Maven Resolver because checksums do not provide security.
This fact is true for the SHA-1 algorithm and the MD5 algorithm.
Industry still uses both algorithms today to verify transport integrity and to detect errors.

To prove that artifacts have not been tampered with, you need signatures such as
those provided by the 
[Maven GPG Plugin](https://maven.apache.org/plugins/maven-gpg-plugin/).

## Checksum Algorithms Service Provider Interface (SPI)

System properties enable users to specify arbitrary checksum algorithms,
even if they are not part of the standard Maven process.
Users can also register an alternate provider for Java Cryptography that
supplies a broader set of message digests for checksums.
The Maven Resolver team discourages this.

To control the supported set of checksums, the Maven Resolver team introduced an SPI for checksums.
We no longer expose `MessageDigest` algorithms directly.
Instead, the SPI supports four checksum algorithms:

* MD5
* SHA-1
* SHA-256
* SHA-512


The names of these algorithms are case-sensitive.

You can use the SPI to extend Maven Resolver with other checksum algorithms.
If Maven Resolver does not provide a required algorithm, you can create a 
factory component for the new algorithm to add it.

We know that users use stronger SHA algorithms.
We do not want to break these configurations.
Configuration and operations remain the same for these users.
However, we want to prevent the future addition of non-standard checksums.

The [Expected Checksums](expected-checksums.html) page explains how and when Maven Resolver uses checksums.

Links:

* [SHA-1](https://en.wikipedia.org/wiki/SHA-1) (The "Data Integrity" section explains this concept.)
* [MD5](https://en.wikipedia.org/wiki/MD5) (The "Applications" section explains the error verification function.)

