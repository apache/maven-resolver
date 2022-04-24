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

Maven Resolver implements "local repository" (that is used by Maven itself
as well), that since the beginning of time was "mixed bag of beans",  
it served twofold purposes: to "cache" the artifacts downloaded from 
remote, but also to "host" the artifacts locally installed (locally built and 
installed, to be more precise). Both of these artifacts were stored in bulk 
in local repository.

Local repository implementations implement the `LocalRepositoryManager` (LRM)
interface, and resolver out of the box provides two implementations for it: 
"simple" and "enhanced". 

## Simple LRM

Simple is fully functional LRM implementation, but is used
mainly in tests, is not recommended in production environments. 

To manually instantiate a simple LRM, one needs to invoke following code:

```java
LocalRepositoryManager simple = new SimpleLocalRepositoryManagerFactory()
        .newInstance( session, new LocalRepository( baseDir ) );
```

Note: this code snippet above instantiates a component, that is not 
recommended way to use it, as it should be rather injected whenever possible. 
This example above is merely a showcase how to obtain LRM implementation 
in unit tests.

## Enhanced LRM

Enhanced LRM on the other hand is "enhanced" with several extra 
features, one most notable is scoping cached content by its origin and context: 
if you downloaded an artifact A1 from repository R1 
and later initiate build that requires same artifact A1, but repository R1 
is not defined in build, the A1 artifact cached from R1 remote repository should be handled
as not present, and needs to be re-downloaded, despite same coordinates.
Those two, originating from two different repositories may not be the same thing.
This is meant to protect users from "bad practice" (artifact coordinates are
unique in ideal world).

### Split local repository

Latest addition to enhanced LRM is "split" feature. By default, "split" 
feature is **not enabled**, enhanced LRM behaves as it behaved in all 
previous versions of resolver.

Enhanced LRM is able to split the content of local repository by 
several conditions:

* differentiate between "cached" and locally "installed" artifacts
* differentiate "cached" artifacts based on their origin (remote repository)
* differentiate between "release" and "snapshot" versioned artifacts

The "split" feature is implemented by `LocalPathPrefixComposer` interface,
that adds different "prefixes" for the locally stored artifacts, based on
their context.

#### Use cases

Most direct use case is simpler local repository eviction. One can delete all
its own locally built artifacts without deleting the cached ones, hence, no
need to re-download the "whole universe". Similarly, deletion of cached ones
can happen based even on origin repository (if split by remote repository 
was enabled beforehand).

Example configuration with split by remote repository:
```java
$ mvn ... -Daether.enhancedLocalRepository.split=true \
          -Daether.enhancedLocalRepository.splitRemoteRepository=true
```

Another use case is interesting for "branched development". Before split feature,
a developer simultaneously working on several branches of same project was forced
to "rebuild all" (better: install all), as same built artifacts from different
branches would still land in local repository on same coordinates, hence, they
would constantly overwrite each other. It was easy to get into "false error"
state, where partial overlapping content were present in local repository from
different branches. Today, one can just define unique "local prefix" for each
branch it is working on (or even by project, like 
`-Daether.enhancedLocalRepository.localPrefix=$PROJECT/$BRANCH`, but use
actual values, these expressions are merely example, there is no interpolation
happening!) and the
local repository becomes usable even simultaneously, even concurrently from
different terminals, as different projects and their branches can simply 
coexist in local repository. They will land in different places, due different
prefixes.

Example configuration for branches:
```java
$ mvn ... -Daether.enhancedLocalRepository.split=true \
          -Daether.enhancedLocalRepository.localPrefix=maven-resolver/mresolver-253
          -Daether.enhancedLocalRepository.splitRemoteRepository=true
```

#### Split repository considerations

**Word of warning**: on every change of "split" parameters, user must be aware
of the consequences. For example, if one change all aspects of split
configuration (all the prefixes), it may be considered logically equivalent 
of defining new local repository, despite local repository root (`-Dmaven.repo.local`) 
is unchanged! Simply put, as all prefixes will be "new", the composed paths will
point to potentially non-existing locations, hence, resolver will consider
is as "new" local repository in each aspect.

#### Implementing own split strategy

To implement own "split" strategy, one need to create an implementation of
`LocalPathPrefixComposerFactory` and override the default implementation
offered by resolver (for example by using Eclipse Sisu priorities for 
components). The factory should create a stateless instance of composer
configured from passed in session, that will be used with enhanced LRM
throughout the session.
