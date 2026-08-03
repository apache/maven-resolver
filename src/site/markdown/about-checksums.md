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

Maven Resolver uses checksums to verify the integrity of downloaded artifacts and metadata.
Checksums exist in repositories next to the target file.
The file extension identifies the checksum algorithm that produced the checksum.
Most Maven repositories contain SHA-1 and MD5 checksums by default.
Maven Resolver also produces these checksums by default.

In the past, Maven Resolver used `java.security.MessageDigest` to calculate checksums.
The Java Cryptography Architecture provides secure one-way hashes.
Maven Resolver used these secure hashes to verify transport integrity.
You can use secure hashes as checksums because hashes and checksums share some functions.

However, cryptographically safe algorithms require many CPU cycles to calculate a checksum.
These checksums provide only integrity verification.
They do not provide security or trust.

If you need trust in your artifacts, you must use signatures.
For example, you can use [GPG Signatures](https://maven.apache.org/plugins/maven-gpg-plugin/).

Some users state that specific algorithms are unsafe or deprecated.
This argument does not apply to Maven Resolver because checksums do not provide security.
This fact is true for the SHA-1 algorithm and the MD5 algorithm.
Industry still uses both algorithms today to verify transport integrity and to detect errors.

## Checksum Algorithms SPI

The user configuration exposes checksum algorithms.
Users can request SHA-256 or SHA-512 through this configuration.
These algorithms are not part of the standard Maven process.
Users can also register an alternate provider for Java Cryptography.
This provider can supply a broader set of message digests for checksums.
The Maven Resolver team considers this a wrong use case.

Users constantly mix the concepts of transport verification and secure hashes.
This confusion occurs because of the historical reasons that we explained previously.

The Maven Resolver team decided to control the supported set of checksums.
We introduced an SPI for checksums.
We no longer expose `MessageDigest` algorithms directly.
This change prevents wrong use cases.
This change also makes it possible to introduce real checksum algorithms.
The set of supported checksum algorithms remains extensible.

If Maven Resolver does not provide a required algorithm, you can add it.
You can create a factory component for the new algorithm.

We know that users use stronger SHA algorithms.
We do not want to break these configurations.
The configuration and operations remain the same for these users.
However, we want to prevent the future addition of non-standard checksums.

## Implemented Checksum Algorithms

Maven Resolver provides these checksum algorithms by default:

* MD5
* SHA-1
* SHA-256
* SHA-512

The names of these algorithms are case-sensitive.
You can use the SPI to extend Maven Resolver with new types of checksum algorithms.

The [Expected Checksums](expected-checksums.html) page explains how and when Maven Resolver uses checksums.

Links:

* [SHA-1](https://en.wikipedia.org/wiki/SHA-1) (The "Data Integrity" section explains this concept.)
* [MD5](https://en.wikipedia.org/wiki/MD5) (The "Applications" section explains the error verification function.)

