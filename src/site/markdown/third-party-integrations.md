# Third-party integrations
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

Maven Resolver provided option for third-party integration using `ServiceLocator` (SL)
from the beginning. Back when SL was implemented, the DI container in use with Resolver and
Maven was the Plexus DI container, that had its own limitations: no constructor injection
among others. Hence, the two was somewhat aligned with each other. Today, with Sisu DI, 
this is not true anymore: we want to use constructor injection for components, but
SL is always getting into our way forcing us to always add default constructor to components.
Hence, decision was made, and SL was deprecated while ago, and is about to be 
dropped in Resolver 2.0.0 release.

Resolver does provide alternative solution for those third-party integrations, where the
(recommended) Sisu DI is not available or cannot be used for some reason. All the
resolver integrations starting with release 1.9.15 can start migrating off the SL
to current solution described below.

## Maven Resolver Supplier

A new simple module `maven-resolver-supplier` serves the purpose to "bootstrap" resolver instance
when there is no desire to use [Eclipse Sisu](https://eclipse.dev/sisu/) DI. It provides one simple class 
`org.eclipse.aether.supplier.RepositorySystemSupplier` that implements `Supplier<RepositorySystem>`
and supplies new, ready-to-use `RepositorySystem` instance for each call. Class is intentionally simplistic,
no instance caching or anything alike is present: all that is concern of caller, just like proper shutdown 
of the created resolver instances is.

The `RepositorySystemSession` should be created using the 
`org.apache.maven.repository.internal.MavenRepositorySystemUtils#newSession()` method
and local repository added to it in usual way (there is no change in this area).

The supplier class is written in a way, to allow easy customization if needed: just extend the class and override
method as needed (all methods are protected).

Consumer/users of this module **must provide SLF4J implementation** in classpath. Resolver uses `slf4j-api` for 
logging purposes, but this module does NOT provide any implementation for it as a dependency. 
It is the consumer/user obligation to provide one at runtime.

Version of `maven-resolver-supplier` artifact in use **must be strictly aligned** with other Resolver artifacts 
on classpath and Maven version.

An example usage (and customization) of supplier can be seen in
[Maven Resolver Ant Tasks 1.5.0](https://github.com/apache/maven-resolver-ant-tasks/blob/maven-resolver-ant-tasks-1.5.0/src/main/java/org/apache/maven/resolver/internal/ant/AntRepositorySystemSupplier.java).

## Resolver configuration

By default, "full resolver experience" is provided:
* for connector, the connector-basic is provided
* for transport the two transport-file and transport-http implementations are provided. If Wagon is needed, add
  transport-wagon as dependency (see Extending Resolver below).

The supplier will provide fully usable instance. To configure resolver, use session user (or 
configuration) properties, when constructing session. All the configuration options are available as 
[listed here](https://maven.apache.org/resolver/configuration.html).

## Extending Supplied Resolver

Extending supplied resolver is simple, and basically requires same three steps for whatever extra you want to include
(like Wagon transport, distributed locking, etc).

* First, you need to include needed module (with transitive deps) to your dependencies.
* Second, you need to customize `RepositorySystemSupplier` by extending it, and to make new components 
  available (by adding `WagonTransporterFactory` to transport factories, or distributed lock factories to lock factories).
* Third, you need to configure session (via user or configuration properties) to make Resolver use newly added components.

