# API Compatibility

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

Maven Resolver exposes 3 modules for clients and those extending Maven Resolver:
* maven-resolver-api (in short API) -- for clients and those extending it
* maven-resolver-spi (in short SPI) -- for those extending it
* maven-resolver-util (in short Util) -- for client and those extending it

Each module guarantees non-breaking (source and binary) compatibility, as long
clients and extenders obey some rules. If you break any of these rules, you are
prone to breakage, and you are on your own.

## Interface And (Abstract) Class Level Contracts

In source, we use two important Javadoc tags to mark intent:
* `@noextend` -- classes (or interfaces) carrying this tag MUST NOT be extended
* `@noimplement` -- interfaces carrying this tag MUST NOT be directly or indirectly implemented, 
  UNLESS the Javadoc of given interface points to an abstract support class that makes indirect
  implementation possible.

Examples:

* `RepositorySystem` interface. It carries both `@noextend` and `@noimplement` tags. This interface
  MUST NOT be extended nor implemented. This is a component interface, that is usually injected into
  client application.
* `TransferListener` interface. It carries both `@noextend` and `@noimplement` tags, but Javadoc
  points at `AbstractTransferListener` as extension point. Hence, clients are NOT allowed to extend
  this interface, nor to directly implement it, but, if custom listener is needed, it is warmly
  advised to extend the given abstract class. This way we can protect you from future breakage.

## Package Level Contracts

Maven Resolver implements customary habit to name packages NOT meant to be accessed by clients. 
If a Java package contains following names:

* `impl`
* `internal`

That Java package is meant as "internal" and does NOT offer guarantees of compatibility as API is. You
may use classes from these packages, but again, you are on your own to deal with (binary or source)
breakages. If you think a class from such package should be "pulled out" and made part of SPI or
maybe API, better inform us via [JIRA](https://issues.apache.org/jira/projects/MRESOLVER): create a
ticket and let's discuss.

As a side note, the count of those names in Java package is directly proportional to possibility of 
breaking changes: the more, the larger the possibility of breakage even in minor releases.

## Version Level Contracts

Maven Resolver does NOT use "semantic versioning", but still tries at best to reflect contained
changes using version number. We use "major.minor.patch" versioning on resolver with following 
semantics:

* On major version change, one should NOT expect any backward compatibility.
* On minor version change, we TRY to keep backward compatibility for those "exposed" 3 modules: 
  API, SPI and Util. Still, there are examples when we failed to do so, usually driven by new 
  features.
* On minor version change, we ENSURE backward compatibility for those "exposed" 3 modules: API, 
  SPI and Util.

In any of three version changes above, in areas where we do not offer guarantees, everything
can happen.

## Outside of Maven

Applications integrating Maven Resolver outside of Maven has really simple job: all they have to
ensure is that API, SPI, Util and the rest of resolver (impl, basic-connector and transports)
have all same versions, and they can rely on  these backward compatibility contracts as explained
above.

## Inside of Maven

Historically, Maven 3.1 (as Maven 3.0 used resolver from different package) provided API, SPI 
and Impl from its own embedded resolver, while Util, Connector, if some plugin or extension
depended on those, was resolved. This caused that a plugin may work with different versions
of API, SPI, Impl or Connector. Given Resolver had API "frozen" for too long time, this was essentially
not a problem, but still weird.

This changes in Maven 3.9+: Maven starting with version 3.9.0 will provide API, SPI, Impl 
**and Util and Connector**. Reason for this change is that Impl and Connector bundled in Maven 
implements things from both, API and SPI, and there was a binary incompatible change between 
Resolver 1.8.0 and previous versions.

Most Resolver users should not be affected by this change.

The binary incompatible change happened in SPI class `RepositoryLayout` as part of work done for 
[MRESOLVER-230](https://issues.apache.org/jira/browse/MRESOLVER-230), and affects both, Connector
and Impl.

## Backward Compatibility Checks

To ensure backward compatibility, starting from 1.9.0 version Maven Resolver uses 
[JApiCmp](https://siom79.github.io/japicmp/MavenPlugin.html),
with two executions (for source and binary level checks). The plugin is enabled on 3 modules of
Resolver mentioned at page top: API, SPI and Util. For "baseline" we use version 1.8.0.
