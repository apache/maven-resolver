# What Is Resolver?
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

Did you ever want to integrate the Maven dependency resolution mechanism into your application? Did you then try to embed Plexus and an entire Maven distribution? Did you want to use the mechanism in a multithreaded way? The stateful singletons in Maven caused problems in that case. Did you ever want more control over the resolved dependency graph? For example, you can use another strategy for conflict resolution, or inspect an intermediate graph.

Resolver (formerly Aether) is the answer. It is an *embeddable Java library that works with artifact repositories*. You can fetch artifacts from remote repositories for local consumption. You can also publish local artifacts to remote repositories so that others can share them.

There are many ways to transfer artifacts, describe their relationships, and use them. Resolver was designed to be open to customization of these aspects, so you can augment or replace the stock functionality. The Resolver Core itself does not know how to handle Maven repositories, for example. It is tool agnostic. It provides a general artifact resolution and deployment framework and leaves details such as the repository format to extensions.

The `maven-resolver-provider` from the Apache Maven project is the most interesting extension. It brings support for Maven repositories. If you want to consume artifacts from the Central Repository, Resolver together with the Maven Resolver Provider is the best choice. This use of Resolver eases your work with artifacts. It also ensures interoperability with other tools that work with Maven repositories.

## How To Embed Resolver

As noted earlier, Resolver alone is not complete. It does not know how to handle Maven repositories and models. To make Resolver minimally complete, you need the `maven-resolver-provider` module. That module contains the required component implementations and the required models for Maven repositories. It gives Resolver only basic functionality.

The next level adds Maven environment awareness. This awareness honors files such as `settings.xml`. You can achieve it with libraries such as MIMA. Maven incorporates Resolver and offers the full experience. To embed Maven is not trivial.
