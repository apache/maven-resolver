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

Did you ever want to integrate Maven's dependency resolution mechanism
into your application and ended up trying to embed Plexus and an entire
Maven distribution? Did you ever want to use Maven's dependency
resolution mechanism in a multithreaded fashion and got burned by the
stateful singletons in there? Did you ever want to have a little more
control over how Maven calculates the resolved dependency graph, say use
another strategy for conflict resolution or inspect some intermediate
dependency graph?

Well, Resolver (former Aether) is the answer. It's an *embeddable Java library to
work with artifact repositories*, enabling you to fetch artifacts from
remote repositories for local consumption and to publish local artifacts
to remote repositories for sharing with others.

There are many ways to transfer artifacts, to describe their
relationships and to use them. Resolver was designed with an open mind
towards customization of these aspects, allowing you to augment or even
replace stock functionality to fit your needs. In fact, the Resolver Core
itself doesn't know how to deal with Maven repositories for instance.
It's tool agnostic and provides some general artifact
resolution/deployment framework and leaves details like the repository
format to extensions.

At this point, the `maven-resolver-provider` from the [Apache
Maven](http://maven.apache.org/) project is probably the most
interesting extension as it brings support for, well Maven repositories.
So if you're looking for a way to consume artifacts from the [Central
Repository](http://search.maven.org/), Resolver in combination with the
Maven Resolver Provider is your best bet. Usage of Resolver in this way does
not only ease your work when dealing with artifacts but also ensures
interoperability with other tools that work with Maven repositories.

## Embedding Resolver

As noted above, Resolver alone is not "complete", in a way, it does not
know how to deal even with Maven repositories (and models). To "minimally
complete" Resolver, one needs `maven-resolver-provider` module, that 
makes Resolver "minimally complete" (contains required component implementations
and introduces required models for Maven repositories). But this is still
just "basic resolver functionality". Next functionality
level is add "Maven environment awareness" (like honoring settings.xml and alike). This 
can be achieved by using libraries like [MIMA](https://github.com/maveniverse/mima)
is. And finally, Maven (that incorporates Resolver) offers full experience (while
embedding Maven is really not trivial).
