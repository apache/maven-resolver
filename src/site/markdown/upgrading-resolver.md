# Upgrading Resolver (major versions)
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

This page will collect quick guides for developers upgrading from one to
another major version of Resolver.

# Upgrading from 1.x to 2.x

Maven Resolver upcoming major version 2.x should be "smooth sailing", as long you
do not depend (directly or indirectly) on **deprecated** classes from Resolver
1.x line. Always use latest 1.x release to check for deprecated classes.

## Session handling changes

Maven Resolver 2.x introduced "onSessionEnd" hooks, that became required for
some of the new features (like HTTP/2 transports are). While existing "Resolver 1.x"
way of handling session will still work, it may produce resource leaks.
Client code **managing Resolver** (like Maven) is strongly advised to upgrade
session handling. Client code **using Resolver** (like Maven Mojos) 
do not have to change anything, they should be able to continue to 
function in very same way as before (as with Resolver 1.x).

What changed on surface:
* introduction of `RepositorySystemSession` nested interfaces `CloseableSession` and `SessionBuilder`.
* introduction of `RepositorySystem` new method `createSessionBuilder` that creates `SessionBuilder` instances.
* deprecation of `DefaultRepositorySystemSession` default constructor, this constructor is actually the "Resolver 1.x way" of using sessions.

Required changes in **consumer project code managing Resolver 2.x**:
* do not use `DefaultRepositorySystemSession` default constructor anymore.
* instead, use `RepositorySystem#createSessionBuilder` to create `SessionBuilder` and out of it `CloseableSession` instances.
* handle sessions as resources: each created instance should be closed once finished their use.
* session instances created by given `RepositorySystem` should be used only with that same instance.
* to shallow-copy session instances (for alteration purposes) using existing `DefaultRepositorySystemSession` copy constructor is acceptable (this is what Mojos do).
* to shallow-copy session instances but have new lifecycle as well, use `SessionBuilder#withRepositorySystemSession` on newly created builder instances.

## Consumer Project Changes (if more needed)

Maven Resolver 2.x now fully delegates multiple aspects to the consumer project, itself remaining "oblivious" about
them. This was done to lessen (or better, fully remove) any overlap between the logic of the Resolver, and it's major
consumer project: Maven. The following aspects are fully delegated to consumer projects:
* Most of the `ArtifactProperties` is deprecated (sans two "core" ones: type and language), as it is really matter of the consumer project assign semantics to them.
* The `ArtifactType` default implementation is deprecated, should be provided by consumer project instead, Resolver 2.x should not provide implementations for it.
* Class path generation (in NodeListGenerator class variations in `org.eclipse.aether.util.graph.visitor` package) and class path filtering (in `DependencyFilterUtils`) is deprecated. It is the consumer app, based on own artifact properties, that can deduce how to build class path (or module path, or anything alike).
* Dependency Scopes are fully removed. For Resolver, they were always "just labels". Resolver is now fully unaware of scopes (and for "system" artifacts has a dedicated handler exposed on session).

For users of Maven Resolver Provider module, these changes will have clean migration path, as all the replacements for 
items from the list above are present in `org.apache.maven:maven-resolver-provider` module. Still, for binary 
compatibility, all affected classes are in fact left in place, but deprecated. Consumer projects should update 
accordingly, and if they do use any deprecated classes from the resolver (affected modules are maven-resolver-api 
and maven-resolver-util), they should switch to their counterparts, that are now present in maven-resolver-provider 
module.

Note: Resolver 2.x still fully supports "legacy" (Maven3-like) functionality, and if existing consumer
code base adapts only the session related changes, consumer project can enjoy new features (like HTTP/2 transport),
while keeping full compatibility, identical to Resolver 1.x release.
