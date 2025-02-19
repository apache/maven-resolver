# Local Repository
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

<!--MACRO{toc|fromDepth=1}-->

Maven Resolver implements a "local repository" (that is used by Maven itself
as well), that since the beginning of time was a "mixed bag of beans", 
it served twofold purposes: to cache the artifacts downloaded from 
remote, but also to store the artifacts locally installed (locally built and 
installed, to be more precise). Both of these artifacts were stored in bulk 
in the local repository.

## Implementations

Local repository implementations implement the `LocalRepositoryManager` (LRM) 
interface, and Resolver out of the box provides two implementations for it: 
"enhanced" used at runtime and "simple" meant to be used in tests and alike
scenarios (is not meant for production use). 

### Enhanced LRM

Enhanced LRM is enhanced with several extra 
features, one most notable is scoping cached content by its origin and context: 
if you downloaded an artifact A1 from repository R1 
and later initiate build that requires same artifact A1, but repository R1 
is not defined in build, the A1 artifact cached from R1 remote repository should be handled 
as not present, and needs to be re-downloaded, despite same coordinates. 
Those two, originating from two different repositories may not be the same thing. 
This is meant to protect users from "bad practice" (artifact coordinates are 
unique in ideal world).

#### Split Local Repository

**Note: Resolver 2.x _renamed related properties_ to clean up configuration
keys, but 2.0.x will support latest Resolver 1.9.x properties to support
transitioning. This document uses "legacy" properties (that work in both,
1.9.x and 2.0.6+ resolver, while current configuration is documented
on [configuration page](configuration.html).**

Latest addition to the enhanced LRM is *split* feature. By default, split 
feature is **not enabled**, enhanced LRM behaves as it behaved in all 
previous versions of Resolver.

Enhanced LRM is able to split the content of local repository by 
several conditions:

* differentiate between *cached* and locally *installed* artifacts
* differentiate *cached* artifacts based on their origin (remote repository)
* differentiate between *release* and *snapshot* versioned artifacts

The split feature is implemented by the `LocalPathPrefixComposer` interface, 
that adds different "prefixes" for the locally stored artifacts, based on 
their context.

##### Note About Release And Snapshot Differentiation

The prefix composer is able to differentiate between release and snapshot 
versioned artifacts, and this is clear-cut: Maven Artifacts are either 
this or that.

On the other hand, in case of Maven Metadata, things are not so clear. 
Resolver is able to differentiate *only* based on `metadata/version` 
field, but that field is not always present. 

Maven Metadata exists in 3 variants:

* G level metadata does not carry version.
* GA level metadata does not carry version.
* GAV level metadata carries version.

In short, G and GA level metadata *always* end up in releases, despite 
GA level metadata body may contain mixed release and snapshot versions enlisted, 
as this metadata *does not contain* the `metadata/version` field.

The GAV level metadata gets differentiated based on version it carries, so 
they may end up in releases or snapshots, depending on their value of 
`metadata/version` field.

##### Use Cases

Most direct use case is simpler local repository eviction. One can delete all 
locally built artifacts without deleting the cached ones, hence, no 
need to re-download the "whole universe". Similarly, deletion of cached ones 
can happen based even on origin repository (if split by remote repository 
was enabled beforehand).

Example configuration with split by remote repository:
```java
$ mvn ... -Daether.enhancedLocalRepository.split \
          -Daether.enhancedLocalRepository.splitRemoteRepository
```

Another use case is interesting for "branched development". Before split feature,
a developer simultaneously working on several branches of same project was forced
to rebuild all (better: install all), as same built artifacts from different
branches would still land in the local repository on same coordinates, hence, they
would constantly overwrite each other. It was easy to get into false error
state where partially overlapping content were present in the local repository from
different branches. Now one can set unique local prefix for each
branch it is working on (or even by project, like 
`-Daether.enhancedLocalRepository.localPrefix=$PROJECT/$BRANCH`, but use
actual values, these expressions are merely an example, there is no interpolation
happening!) and the
local repository becomes usable even simultaneously, even concurrently from
different terminals, as different projects and their branches can simply 
coexist in local repository. They will land in different places, due different
prefixes.

Example configuration for branches:
```java
$ mvn ... -Daether.enhancedLocalRepository.split \
          -Daether.enhancedLocalRepository.localPrefix=maven-resolver/mresolver-253
          -Daether.enhancedLocalRepository.splitRemoteRepository
```

For complete reference of enhanced LRM configuration possibilities, refer to 
[configuration page](configuration.html). That page contains current configuration
keys for Resolver 2.x, while this page example use "legacy" keys that work in
both Resolver 1.9.x and Resolver 2.0.6+.

##### Split Repository Considerations

**Word of warning**: on every change of "split" parameters, user must be aware
of the consequences. For example, if one change all aspects of split
configuration (all the prefixes), it may be considered logically equivalent 
of defining a new local repository, despite local repository root (`-Dmaven.repo.local`) 
is unchanged! Simply put, as all prefixes will be "new", the composed paths will
point to potentially non-existing locations, hence, resolver will consider
it as a "new" local repository in every aspect.

##### Implementing Custom Split Strategy

To implement custom split strategy, one needs to create a component of
type `LocalPathPrefixComposerFactory` and override the default component
offered by Resolver (for example by using Eclipse Sisu priorities for 
components). This should be done by extending `LocalPathPrefixComposerFactorySupport` 
class that provides all the defaults.

The factory should create a stateless instance of a composer
configured from passed in session, that will be used with the enhanced LRM
throughout the session.

### Simple LRM

Simple is a fully functional LRM implementation, but is used
mainly in tests, it is not recommended in production environments.

To manually instantiate a simple LRM, one needs to invoke following code:

```java
LocalRepositoryManager simple = new SimpleLocalRepositoryManagerFactory()
        .newInstance( session, new LocalRepository( basedir ) );
```

Note: This code snippet above instantiates a component, that is not
recommended way to use it, as it should be rather injected whenever possible.
This example above is merely a showcase how to obtain LRM implementation
in unit tests.

## Shared Access to Local Repository

In case of shared (multi-threaded, multi-process or even multi host) access
to local repository, coordination is a must, as local repository is hosted
on file system, and each thread may read and write concurrently into it,
causing other threads or processes to get incomplete or partially written data.

Hence, since Resolver 1.7.x version, there is a pluggable API called "Named Locks" 
available, providing out of the box lock implementations for cases like:

* multi-threaded, in JVM locking (the default)
* multi-process locking using file system advisory locking
* multi-host locking using Hazelcast or Redisson (needs Hazelcast or Redisson cluster)

For details see [Named Locks module](maven-resolver-named-locks/).

## Error Handling and Caching

Each artifact/metadata which cannot be resolved leads to an error either classified as 

1. *not found* error or
2. (any) *other* error (for authentication issues, timeouts etc.)

The caching behavior for both error types can be be configured programmatically via `org.eclipse.aether.DefaultRepositorySystemSession.setResolutionErrorPolicy(...)`.

In case caching is enabled for any of the two classifications a Java Properties file is created/updated (with the same filename as the cached artifact in the success case would get but with the additional suffix `.lastUpdated`) in the local repository. Within that file the key `<canonical-remote-url>.error` is updated/added. Its value either contains the error message (for type 2 resolver errors) or is empty (for type 1 resolver errors).

### Configuration in Maven

In Maven 3 resolver errors of **type 1** are **always cached** and the ones of **type 2** are **never cached**.

With [Maven 4](https://issues.apache.org/jira/browse/MNG-7653) the caching of type 1 errors can be disabled with CLI option `-canf false` (still enabled by default). However, the caching of type 2 errors is disabled without any way to override that policy even in Maven 4.
