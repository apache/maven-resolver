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

Maven Resolver exposes three modules for client applications and extensions.
Client applications invoke methods in these modules.
Extensions inherit from classes and implement interfaces.

* `maven-resolver-api` (API) - Client applications and extensions use this module.
* `maven-resolver-spi` (SPI) - Extensions use this module.
* `maven-resolver-util` (Util) - Client applications and extensions use this module.

If you obey specific rules, these modules will be source and binary compatible across minor releases.
If you break these rules, your code can break when you update these modules.

## Interface And (Abstract) Class Level Contracts

We use two Javadoc tags in the source code to mark intent:
* `@noextend` - You must not extend classes or interfaces with this tag.
* `@noimplement` - You must not implement interfaces with this tag directly or indirectly.

If the Javadoc points to an abstract support class, you can implement the `@noimplement` interface indirectly.

Examples:

The `RepositorySystem` interface has the `@noextend` tag and the `@noimplement` tag.
You must not extend or implement this interface.
The `RepositorySystem` interface is a component interface.
Client applications usually receive this interface through dependency injection.

The `TransferListener` interface has the `@noextend` tag and the `@noimplement` tag.
The Javadoc points to the `AbstractTransferListener` abstract class.
You must not extend or implement the `TransferListener` interface directly.
If you need a custom listener, extend the `AbstractTransferListener` abstract class.
This abstract class protects your code from future breakages.

## Package Level Contracts

Maven Resolver identifies internal Java packages with the words `impl` and `internal`.
These internal packages do not guarantee compatibility between releases.
If you use classes from these packages, you must fix source breakages and binary breakages yourself.
You can request to move a class to the API or the SPI through a ticket on [GitHub](https://github.com/apache/maven-resolver/issues).

## Version Level Contracts

Maven Resolver does not use "semantic versioning".
However, Maven Resolver uses a "major.minor.patch" version format to indicate changes.
Major version changes do not provide backward compatibility.
The API, SPI, and Util modules should be backwards compatible across minor version changes.
However, we have violated this rule in the past, usually to support new features.

Maven Resolver does not guarantee compatibility for internal modules.
Internal modules can change in any version update.

## Outside of Maven

Applications can use Maven Resolver outside of Maven.
These applications must use the same version for all Maven Resolver modules.
For example, the API, SPI, Util, `impl`, `basic-connector`, and transports must share the same version.
If the versions match, the applications can rely on the compatibility guarantees.

## Inside of Maven

In the past, Maven 3.1 provided the API, SPI, and `impl` modules from an embedded resolver.
Plugins resolved the Util and Connector modules separately.
Therefore, plugins used different versions of these modules.
The static API prevented major problems.

Maven 3.9.0 provides the API, SPI, `impl`, Util, and Connector modules.
The bundled `impl` and Connector modules implement the API and the SPI.
A binary incompatibility occurred between Maven Resolver 1.8.0 and previous versions.
Because of this incompatibility, Maven 3.9.0 bundles all modules to ensure stability.

This change does not affect most Maven Resolver users.

The binary incompatibility occurred in the `RepositoryLayout` SPI class for [MRESOLVER-230](https://issues.apache.org/jira/browse/MRESOLVER-230).
This incompatibility affects the Connector module and the `impl` module.

## Backward Compatibility Checks

Maven Resolver uses [JApiCmp](https://siom79.github.io/japicmp/MavenPlugin.html) to verify backward compatibility.
Starting with version 1.9.0, Maven Resolver runs this plugin twice to verify source compatibility and binary compatibility.
The plugin runs on the API, SPI, and Util modules.
The compatibility baseline is version 1.8.0.
