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

Remote Repository Filtering (RRF) is a long asked feature of Maven, and plays a huge role when your build uses
several remote repositories. In such cases Maven "searches" the ordered list (effective POM) of remote repositories,
and artifacts get resolved using a loop and a "first found wins" strategy. This has several implications:

* your build gets slower, as if your artifact is in the Nth repository, Maven must make N-1 requests that will result in
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

The RRF feature offers a filter source SPI for 3rd party implementors, but it also provides 2 out of the box 
implementations for filtering: "prefixes" and "groupId" filters.

Both implementation operate with several files (per remote repository), and they use the term "filter basedir". By
default, filter basedir is resolved from local repository root and resolves to `${localRepo}/.remoteRepositoryFilters`
directory. It will be referred to in this document with `${filterBasedir}` placeholder.

To explicitly set filter basedir, use following setting: `-Daether.remoteRepositoryFilter.${filterName}.basedir=somePath`, 
where "somePath" can be relative path, then is resolved from local repository root, or absolute path, then is used as is.

Since Maven 3.9.x you can use expression like `${session.rootDirectory}/.mvn/rrf/` to store filter data along with 
sources as `session.rootDirectory` will become an absolute path pointing to the root directory of your project (where
usually the `.mvn` directory is).

Both implementation without input files (being present) behave as disabled for given repository. Moreover, the
enabled settings suffixed with ".repoId" can be used to selectively enable or disable filtering for given repository
(for example `-Daether.remoteRepositoryFilter.prefixes.myrepo=false`).

Unlike in Resolver 1.x, the filtering is **by default enabled**, and prefixes will be dynamically discovered, and
if found, used. For groupId filter user intervention is still needed to provide input files. Hence, without these,
only prefix filtering will automatically kick in.

### The Prefixes Filter

The "prefixes" named filter relies on a file containing a list of "repository prefixes" available from a given repository.
The prefix is essentially the "starts with" of Artifact path as translated by the repository layout. Its effect is that
only those artifacts will be attempted to be downloaded from given remote repository, if there is a
"starts with" match between the artifact path translated by the layout, and the prefixes file published by remote repository.

Prefixes are usually published by remote repositories, hence, are kinda filtering the other way around:
it is rather the remote repository advising us "do not even bother to come to me with a path that has no
appropriate prefix enlisted in this file". On the other hand, having a prefix enlisted does not
provide 100% guarantee that a matching artifact is really present! For example the presence of `/com/foo`
prefix does NOT imply that `com.foo:baz:1.0` artifact is present, it merely tells "I do have
something that starts with `/com/foo`" (for example `com.foo.baz:lib:1.0`). The depth of published
prefixes is set by the publisher, and is usually a value between 2 and 4. It all boils down to the balance between
"best coverage" and "acceptable file size" (ultimately, the prefixes file containing all the relative
paths of deployed artifacts from the repository root would be 100% coverage, but the cost would be a huge
file size for huge repositories like Maven Central).

As this file is (automatically) published by MC and MRMs, and using them is the simplest: they will be automatically
discovered and cached (just like any artifact from given remote repository).

Manual authoring of these files, while possible, is not recommended. The best is to keep them up to date by
downloading the published files from the remote repositories. In ideal circumstances no user intervention is needed
as remote repository should publish prefix file and discovery should discover it.

Many MRMs and Maven Central itself publish these files. Some prefixes file examples:
* Maven Central [prefixes.txt](https://repo.maven.apache.org/maven2/.meta/prefixes.txt)
* ASF Releases hosted repository [prefixes.txt](https://repository.apache.org/content/repositories/releases/.meta/prefixes.txt)

The user provided prefixes files are expected in the following location by default: 
`${filterBasedir}/prefixes-${remoteRepository.id}.txt`.

**Important**: Valid prefix files start with following "magic" on their very first line: `## repository-prefixes/2.0`.
If the first line in file is not this string, the prefix file is discarded.

To disable prefixes filter, use the following setting: `-Daether.remoteRepositoryFilter.prefixes=false`.
To disable for single repository filtering, append to key `.repoId`.

The prefixes filter will "abstain" from filtering for the given remote repository, if there was no prefix file discovered,
nor there is user input provided for it.

### The GroupId Filter

The "groupId" named implementation is filtering based on allowed `groupId` of Artifact. In essence, it is a list
of "allowed groupId coordinates from given remote repository". The file contains one Artifact groupId per line along with
possible modifiers.

The groupId files are expected in the following location by default: 
`${filterBasedir}/groupId-${remoteRepository.id}.txt`.

To disable groupId filtering, use the following setting: `-Daether.remoteRepositoryFilter.groupId=false`.
To disable for single repository filtering, append to key `.repoId`.

The groupId filter will "abstain" from filtering for the given remote repository, if there is no input provided for it.

The GroupId filter allows the "recording" of encountered groupIds as well, that can be used as
starting point: after the "recording" is done, one can edit, remove or add entries as needed. When the
groupId filter is set to "record", it does NOT filter, but instead collects all the encountered
groupIds per remote repository and saves them into properly placed file(s).

To enable GroupId Filter recording, use following setting: `-Daether.remoteRepositoryFilter.groupId.record=true`.

To truncate recorded file(s) instead of merging recorded entries with existing file, use following setting:
`-Daether.remoteRepositoryFilter.groupId.truncateOnSave=true`. If enabled, the saved file will contain ONLY
the groupIds that were recorded in current session, otherwise the recorded groupIds and already present ones
in file will be merged, and then saved.

Format of file:
* Lines beginning with `#` (hash) and blank lines are ignored
* modifier (must be first character) `!` is negation (disallow; but default entry "allow")
* modifier (must be first, or second if negation modifier present) `=` is limiter (equals; by default entry is "and below this G")
* a proper Maven groupId, like `org.apache.maven`

Example file:
```
# My file                   (1)
                            (2)
org.apache.maven            (3)
!=org.apache.maven.foo      (4)
!org.apache.maven.indexer   (5)
=org.apache.bar             (6)
```

Lines 1 and 2 are ignored. Line 3 means "allow `org.apache.maven` G and below". Line 4 is "disallow org.apache.maven.foo"
only" (so `org.apache.maven.foo.bar` is allowed due first line). Line 5 means "disallow `org.apache.maven.indexer` and below"
and finally line 6 means "allow `org.apache.bar` ONLY" (so `org.apache.bar.foo` is NOT enabled).

One can use one special entry "root" `*` (asterisk) to define the "default acceptance" (that without it 
defaults to REJECTED). Similarly, adding `!*` to file defines "default acceptance" of FALSE/REJECTED as well, 
and adding it to file changes nothing, as this is the default acceptance (but may serve some documentation purposes). 
Be aware: In case a line with single asterisk `*` is present, the whole logic of Group filter is getting inverted, 
hence there is no need to add "allowed entries" (they are allowed by default), but one can add "disallowed entries" by 
adding `!com.foo` and alike.

Conflicting rules: rule parser is intentionally trivial, so in case of conflicting rules the "first wins" strategy is 
applied. Ideally, user should keep files sorted or handle them in a way one can detect conflicts in it.

## Operation

To make RRF filters operate, as they are by default enabled, you have to make sure that:
* prefix file can be discovered (if not for any reason, you may provide alternate input for it)
* groupId is procided.

As said above, enabled filters does not make them active (participate in filtering): if a given remote repository does not have 
any input available, the filter pulls out from "voting" (does not participate in filtering, will abstain 
from voting). Same effect can be achieved by selectively enable filter by appending `.repoId` to property key.

The most common configuration in case of multiple remote repositories is the following setup: use both filters, 
the Maven Central prefixes should be discovered (same for any other remote repository that offers prefixes).
Optionally provide groupId files for non-Central remote repositories, if needed. It results in following filter 
activity:

| Remote Repository | Prefixes Filter    | GroupId Filter |
|-------------------|--------------------|----------------|
| Maven Central     | active             | inactive       |
| Some Remote       | active or inactive | active         |

This leads to the following "constraints":
* "Maven Central" is asked only for those artifacts it claims it may have (prefixes)
* "Some Remote" is asked only for allowed groupIds. If it publishes prefixes, is even better: you will not ask for things it for sure does not have.
