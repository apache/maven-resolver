# Creating a RepositorySystemSession
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

Resolver (former Aether) and its components are stateless. You must pass all configuration and state into the methods. When you make multiple requests to resolve dependencies, many settings remain the same across the method calls. These settings include the proxy settings and the path to the local repository. An instance of `org.eclipse.aether.RepositorySystemSession` represents the settings that remain the same for the entire usage session of the repository system.

You can create such a session with classes from `maven-resolver-supplier`. The code below creates a session that mimics the Maven setup.

```java
import org.eclipse.aether.supplier.RepositorySystemSupplier;

...
    private static RepositorySystemSession newSession( RepositorySystem system )
    {
        RepositorySystemSession.SessionBuilder sessionBuilder = system.createSessionBuilder();

        LocalRepository localRepo = new LocalRepository( "target/local-repo" );
        sessionBuilder.withLocalRepositories( localRepo );

        return sessionBuilder.build();
    }
```

Only the local repository must be specified. The other settings are initialized with default values.

Read the API documentation for `RepositorySystemSession.SessionBuilder` to learn about all the settings for a session.

If you use a Maven plugin or run code embedded in Maven, the session is already created for you. You can derive a new session with the copy constructor of `DefaultRepositorySystemSession` if you must alter the session.

To read configuration from the user's `settings.xml` in a non-Maven project, use the [MIMA](https://github.com/maveniverse/mima) library. MIMA provides the necessary parts. Direct any questions about MIMA to the Maven mailing list.
