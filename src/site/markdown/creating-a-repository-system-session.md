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

Resolver (former Aether) and its components are designed to be stateless and as such all
configuration/state has to be passed into the methods. When one makes
multiple requests to resolve dependencies, a fair amount of settings
usually remains the same across these method calls, like the proxy
settings or the path to the local repository. Those settings that tend
to be the same for an entire usage session of the repository system are
represented by an instance of
`org.eclipse.aether.RepositorySystemSession`. Using classes from
`maven-resolver-supplier`, creating such a session that mimics Maven's
setup can be done like this:

```java
import org.eclipse.aether.supplier.RepositorySystemSupplier;

...
    private static RepositorySystemSession newSession( RepositorySystem system )
    {
        RepositorySystemSession.SessionBuilder sessionBuilder = SessionBuilderSupplier.get();

        LocalRepository localRepo = new LocalRepository( "target/local-repo" );
        sessionBuilder.setLocalRepositoryManager( system.newLocalRepositoryManager( session, localRepo ) );

        return session.build();
    }
```

As you see, the only setting that must be specified is the local
repository, other settings are initialized with default values. Please
have a look at the API docs for `RepositorySystemSession.SessionBuilder` to
learn about all the other things you can configure for a session.

In case of Maven plugin, or when code runs embedded in Maven, the session
is already created for you, but you can still "derive" using copy constructor
of `DefaultRepositorySystemSession` if some session alteration is needed.

If you seek a closer cooperation with [Apache
Maven](http://maven.apache.org/) and want to read configuration from the
user's `settings.xml`, you should have a look at the library
[MIMA](https://github.com/maveniverse/mima) which provides the necessary
bits. Please direct any questions regarding usage of that library to the
Maven mailing list.
