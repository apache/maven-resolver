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

# Version comparison cases

`version-comparison-cases.tsv` is a language-independent data set for comparing two
version strings. It is deliberately plain text so that other implementations can reuse it
without depending on this Java test suite.

## Format

One case per line, three tab-separated fields:

```
<left version>	<right version>	<expected>
```

Expected is one of:

| Value | Meaning |
| --- | --- |
| `LT` | left < right |
| `EQ` | left == right |
| `GT` | left > right |

Lines beginning with `#` are comments and are ignored. Fields may be empty; an empty
version string is a valid case.

## Consumers

- `VersionComparisonDataTest` runs every case against
  `org.eclipse.aether.util.version.GenericVersion`.
- The same file can drive equivalent tests in Maven core's `ComparableVersion`, Gradle or
  any semver implementation. If an implementation disagrees with a case, inspect the case
  before changing the data: the cases encode established Resolver behaviour.

## Adding a case

Append a line with the two versions and the expected relation. Prefer adding the case here
rather than only in a Java test, so every consumer benefits from it.
