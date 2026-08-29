<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Contributing to Apache Maven Artifact Resolver

[Apache Maven Artifact Resolver](https://maven.apache.org/resolver/) is a library
for working with artifact repositories and dependency resolution.

Thank you for your interest in contributing to Apache Maven Artifact Resolver!
Contributions of bug fixes, improvements, documentation, tests, and new features
are welcome.

Before starting work, please review the following guidelines.

## Getting Started

### 1. Set up your GitHub account

Make sure you have a [GitHub account](https://github.com/signup/free).

### 2. Check existing issues and pull requests

Before starting work, search the existing GitHub issues and pull requests to
make sure the problem or feature is not already being worked on.

Avoid duplicating existing work whenever possible.

### 3. Discuss new features

If you are planning to implement a new feature, discuss the proposed changes
on the [developer mailing list][ml-list] first.

This helps determine whether the proposed change is appropriate for Apache
Maven and can prevent contributors from spending time on changes that may not
fit the project's scope.

### 4. Create or find an issue

If an issue does not already exist, create a ticket describing the problem or
proposed improvement.

For bug reports, include:

- A clear description of the problem.
- Steps to reproduce the issue.
- The earliest version where the issue is known to occur.
- Any relevant logs, configuration, or other information.

### 5. Fork the repository

Fork the Maven Artifact Resolver repository on GitHub and clone your fork
locally.

## Making Changes

### Create a topic branch

Create a topic branch from the branch on which you want to base your work.
This is usually the `master` branch.

For example:

```bash
git checkout master
git fetch upstream
git merge upstream/master
git checkout -b my-change