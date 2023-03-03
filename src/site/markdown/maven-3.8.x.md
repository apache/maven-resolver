# Using with Maven 3.8.x
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

Since version 1.7.0, Maven Resolver requires Java 8 to run and a brand new default sync context
factory has been implemented. Both are not compatible with Maven 3.8.x anymore which still requires
Java 7 to run. Maven 3.8.x will continue to use version 1.6.x which you can find
[here](/resolver-archives/resolver-1.6.3/).
This also means that you cannot make use of the features provided by version 1.7.0 and later.
If you require the changes from this version, but must use Maven 3.8.x, you can build yourself an adapted version
of Maven from the branch [`maven-3.8.x-resolver-1.8.x`](https://github.com/apache/maven/tree/maven-3.8.x-resolver-1.8.x)
with Java 8 requirement or use signed binaries and source from the [dev dist area](https://dist.apache.org/repos/dist/dev/maven/maven-3/3.8.x-resolver-1.8.x/)
and use it as if you would use Maven 3.9.x.
