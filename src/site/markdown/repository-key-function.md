# Repository Key Function
<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

One long outstanding issue in Maven (across all versions) was how to identify
remote repositories (this problem mostly tackles them, as local, workspace
and other repositories are usually "singletons" and have fixed IDs).

Existing Maven versions mostly limited themselves to `RemoteRepository#getId()`
method to "key" repositories, but this strategy many times proves suboptimal.

Known issues that Maven users cannot fight against:
* different IDs for same URLs, examples (from Central) are `apache-snapshots` (plural), `apache-snapshot` (singular) 
  or `apache.snapshot` (dot vs dash) defined repositories, that all point to same ASF snapshot repository.
* same IDs for different URLs (two totally disconnected project may define repository `project-releases` in their POM, 
  while in fact those two repositories are not related at all)
* repository IDs that are [not file-friendly](https://github.com/apache/maven-resolver/issues/1564). Usually this should 
  be impossible, as Maven validates and forbids these characters in ID field, but in some cases 
  (ancient or generated POMs) this may happen.

Remote repositories that user cannot "fix", usually enter the build via those POMs that are not authored by user
themselves, so project POM and parent POMs can be safely excluded. In turn, these may come from POMs that are
being pulled in as third-party plugin or dependency POMs.

While we don't find the first issue deal-breaker (and we did not provide yet a function for fixing it), the latter two
may produce various problems with local repository, split local repository and so on, causing a total mix-up of expected
layout, or even wrongly grouped artifacts.

For those eager to fully control used repositories, Maven 3.9.x line added the `-itr`/`--ignore-transitive-repositories`
CLI option, but while this solves the problem, it does it by fully delegating the work onto the user itself, to define
all the needed remote repositories (for dependencies but also for plugins) in project POM that build requires. 
In certain cases this option is the recommended way, but many times it proves too burdensome.

Hence, Maven Resolver 2.x introduces notion of "repository key function", which is a function that creates 
Remote Repository "key", with following properties:
* can be used to identify a `RemoteRepository`
* is configurable (see below)
* produced keys are "file system friendly" as well

Latest Resolver uses repository key at these places (and these must be aligned; must use same function):
* `EnhancedLocalRepositoryManager`, the default LRM, where artifact availability is being calculated
* `LocalPathPrefixComposer`, in case of "split local repository" to calculate prefix/suffix elements based on artifact originating repository (if enabled)
* `RemoteRepositoryManager` that consolidates existing and newly discovered repositories (by eliminating them or merging mirrors, as needed)

In these cases, the repository key function affects how Resolver (and hence, Maven) works _fundamentally_, what 
`RemoteRepository` it considers "same" or "different". Which artifacts are considered as coming from "same origin"
or "different origin" (i.e. split local repository).

Furthermore, repository key function (possibly different one) is used in two components to map remote repository configuration to file paths:
* Trusted Checksums Source
* Remote Repository Filter

In these cases, the repository key function only role is to provide "file system friendly" path segments based on
`RemoteRepository` instances.

**Important implication:** When Resolver/Maven is reconfigured to use alternative repository key function, it is
worthwhile to start with new, empty local repository (as keys are used in LRM maintained metadata).

## Implemented Repository Key Functions

The function is configurable, while the default function remains Maven 3.x compatible. The existing functions are:
* `simple` (Maven 3 default; technically equivalent to `nid`)
* `nid` (default)
* `nid_hurl`
* `ngurk`

These below are recommended only for some special cases:
* `hurl`
* `gurk`

## Recommended New Repository Key Functions

### `nid`

This key still relies solely on `RemoteRepository#getId()` but applies transformation to returned value to make it
"file system path segment friendly". Is usable in the simplest use cases, and behaves as Maven 3 did.
Technically is equivalent to legacy `simple` repository key function.

### `nid_hurl`

This key relies on `RemoteRepository#getId()` and `RemoteRepository#getUrl()`, and forms a key based on these two.
This means if you have same-ID repository pointing to two different URLs, they will be considered different. Still,
on disk the produced key string is user-friendly, as ID remains readable.

### `ngurk`

This key relies on **all properties** (details below) of `RemoteRepository`, but is "normalized" in a way that only the 
fact that a `RemoteRepository` is a mirror (or not) is recorded, while the list of the mirrored repositories does not 
affect key production. This also means that if you have two "similar" `RemoteRepository`, with same ID, same URL, but
one has snapshots enabled, the other snapshots disabled, they will be considered different.

This function leaves out following `RemoteRepository` properties: `Authorization`, `Proxy`, `Intent`, `Mirrors` 
(but checks is list empty or not) and update policies for releases and snapshots.

## Special Repository Key Functions

These functions are **not recommended for everyday use**, but may prove useful in some cases.

### `hurl`

This key relies solely on `RemoteRepository#getUrl()`.
This means that repository URL becomes what repository ID was for equality check. Note: this function does not perform
any kind of URL "normalization", URL is used as-is. The problem with this function is that it will produce
"human unfriendly" repository key that is fully disconnected and hard to trace back to origin repository.

### `gurk`

Similar to `ngurk` but does not normalize mirrors. As a consequence, and due dynamism of mirrors, the key of same
remote repository (for example `<mirrorOf>external:*</mirrorOf>`) **may change during the build**.
