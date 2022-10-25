# Remote Repository Filtering
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

A new Maven Resolver feature that allows filtering of Artifact by RemoteRepository based on various (extensible) 
criteria.

### Why?

Remote Repository Filtering (RRF) is a long asked feature of Maven, and plays huge role when your build uses
several remote repositories. In such cases Maven "searches" the ordered list (effective POM) of remote repositories,
and artifact gets resolved using loop and "first found wins" strategy. This have several implications:

* your build gets slower, as if your artifact is in Nth repository, Maven must make N-1 requests that will result in
  404 Not Found only to get to Nth repository to finally get the artifact.
* you build "leaks" artifact requests, as those repositories are asked for artifacts, that does not (or worse,
  cannot) have them. Still, those remote repository operators do get your requests in access logs.
* to "simplify" things, users tend to use MRM "group" (or "virtual") repositories, that causes  data loss on
  Maven Project side (project loses artifact origin information) and ends up in disasters, as at the end these
  "super-uber groups" grow uncontrollably, their member count become huge (as new members are being
  added as time passes), or created groups count grows uncontrollably, and project start losing the knowledge
  about their required remote repositories, needed to (re)build a project, hence these projects become
  un-buildable without the MRM, projects become bound to MRM and/or environment that is usually out of project
  control.

Maven by default gets slower as remote repositories are added, leaks your own build information to remote
repository operators, and current solutions offered to solve this problem just end up in disasters (most often).

### What It Is?

Imagine you can instruct Maven which repository can contain what artifact? Instead of "ordered loop" searching
for artifacts in remote repositories, Maven could be instructed in controlled way to directly reach only the
needed remote repository.

With RRF, Maven build does NOT have to slow down with new remote repositories added, and will not leak either
build information anywhere, as it will get things from where they should be get from.

### What It Is Not?

When it solely comes to dependencies, don't forget
[maven-enforcer-plugin](https://maven.apache.org/enforcer/enforcer-rules/bannedDependencies.html) rules that are 
handling exactly them. RRF is NOT an alternative means to these enforcer rules, it is a tool to make your build
faster and more private, optimized, without loosing build information (remote repositories should be in POM).

### Maven Central Is Special

Maven Central (MC) repository is special in this respect, as Maven will always try to get things from here, as your build,
plugins, plugin dependencies, extension, etc. will most often come from here. While you CAN filter MC, filtering MC is
most often a bad idea (filtering, as in "limiting what can come from it"). On other hand, MC itself offers help
to prevent request leakage to it (see "prefixes" filter).

So, **most often** limiting "what can be fetched" from MC is a bad idea, it **can be done** but in very, very cautious way,
as otherwise you put your build at risk. RRF does not distinguish the "context" of an artifact, it merely filters them out
by (artifact, remoteRepository) pair, and by limiting MC you can easily get into state where you break your build (as
plugin depends on filtered artifact).

## RRF

The RRF feature offer filter source SPI for 3rd party implementors, but it also provides 2 out of the box 
implementations for filtering: "prefixes" and "groupId" filters.

Both implementation operate with several files (per remote repository), and they use the term "filter basedir". By
default, filter basedir is resolved from local repository root and resolves to `${localRepo}/.remoteRepositoryFilters`
directory, and will refer to it in this document with `${filterBasedir}` placeholder.

To explicitly set filter basedir, use following setting: `-Daether.remoteRepositoryFilter.${filterName}.basedir=somePath`, 
where "somePath" can be relative path, then is resolved from local repository root, or absolute path, then is used as is.

### The Prefixes Filter

The "prefixes" named filter relies on file containing list of "repository prefixes" available from given repository.
The prefix is essentially "starts with" of Artifact path as translated by Repository Layout. Its effect is that
only those artifacts will be attempted to be downloaded from given remote repository, if there is a
"starts with" match between artifact path translated by layout, and prefixes file published by remote repository.

Prefixes are usually published by remote repositories, hence, are kinda filtering other way around:
is rather remote repository advising us "do not even bother by coming to me with a path that has no
appropriate prefix enlisted in this file". On the other hand, having a prefix enlisted does not
provide 100% guarantee that matched artifact is really present! For example presence of `/com/foo`
prefix does NOT imply that `com.foo:baz:1.0` artifact is present, it merely tells "I do have
something that starts with `/com/foo`" (for example `com.foo.baz:lib:1.0`). The depth of published
prefixes is set by publisher, and is usually value between 2 and 4. It all boils down to equilibrium of
"best coverage" and "acceptable file size" (ultimately, prefixes file containing all the relative
paths of deployed artifact from repository root would be 100% coverage, but the cost would be huge
file size for huge repositories like Maven Central).

As this file is (automatically) published by MC and MRMs, using them is the simplest. Manual authoring
of these files, while possible, is not recommended. Best is to keep them up to date by
downloading published files from remote repositories.

Many MRMs and Maven Central itself publishes this file. Some prefixes file examples:
* Maven Central [prefixes.txt](https://repo.maven.apache.org/maven2/.meta/prefixes.txt)
* ASF Releases hosted repository [prefixes.txt](https://repository.apache.org/content/repositories/releases/.meta/prefixes.txt)

The prefixes files are expected in following location by default: 
`${filterBasedir}/prefixes-${remoteRepository.id}.txt`.

To enable prefixes filter, use following setting: `-Daether.remoteRepositoryFilter.prefixes=true`.

The prefixes filter "abstains" from filtering for given remote repository, if there is no input for it provided.

### The GroupId Filter

The "groupId" named implementation is filtering based on allowed `groupId` of Artifact. In essence, is a list
of "allowed groupId coordinates from given remote repository". The file contains Artifact groupId per one line.

The groupId files are expected in following location by default: 
`${filterBasedir}/groupId-${remoteRepository.id}.txt`.

To enable groupId filter, use following setting: `-Daether.remoteRepositoryFilter.groupId=true`.

The groupId filter "abstains" from filtering for given remote repository, if there is no input for it provided.

The GroupId filter allows "recording" of encountered groupIds as well, that can be used as
starting point: after "recording" done, one can edit it, remove or add entries as needed. When
groupId filter set to "record", it does NOT filter, but instead collects all the encountered
groupIds per remote repository and saves them into properly placed file(s).

To enable GroupId Filter recording, use following setting: `-Daether.remoteRepositoryFilter.groupId.record=true`.

To truncate recorded file(s) instead of merging recorded entries with existing file, use following setting:
`-Daether.remoteRepositoryFilter.groupId.truncateOnSave=true`. If enabled, the saved file will contain ONLY
the groupIds that were recorded in current session, otherwise the recorded groupIds and already present ones
in file will be merged, and then saved.

## Operation

To make RRF filter operate, you have to provide two things: you have to explicitly enable the filter, and you have to
provide input for the filter. 

Enabling filters does not make them active (participate in filtering): if a given remote repository does not have 
any input available, the filter pulls out from "voting" (does not participate in filtering, abstains 
from voting).

In short, enabling filters is not enough, to make it active for a remote repository, you
must provide them with "input data" for this remote repository as well.

The most common case in case of multiple remote repositories is following setup: enable both filtering, and
provide Maven Central prefixes file (downloaded) and if any remote repository offers prefixes, download them
as well. Optionally provide groupId files for other remote repositories, if needed. It results in following filter 
activity:

| Remote Repository | Prefixes Filter | GroupId Filter |
|-------------------|-----------------|----------------|
| Maven Central     | active          | inactive       |
| Some Remote       | inactive        | active         |

It results in following "constraints":
* "Maven Central" is asked only for those artifacts it claims it may have (prefixes)
* "Some Remote" is asked only for allowed groupIds. If it publishes prefixes, it can be safely added as well.
