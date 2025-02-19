# Resolving Dependencies
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

Extending the code snippets from [Creating a RepositorySystemSession](creating-a-repository-system-session.html), the snippet below demonstrates
how to actually resolve the transitive dependencies of say
`org.apache.maven:maven-core:3.9.6` and to dump the result as a class
path to the console:

```java
    public static void main( String[] args )
        throws Exception
    {
        RepositorySystem repoSystem = newRepositorySystem();

        RepositorySystemSession session = newSession( repoSystem );

        Dependency dependency =
            new Dependency( new DefaultArtifact( "org.apache.maven:maven-core:3.9.6" ), "compile" );
        RemoteRepository central = new RemoteRepository.Builder( "central", "default", "https://repo.maven.apache.org/maven2/" ).build();

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot( dependency );
        collectRequest.addRepository( central );
        DependencyNode node = repoSystem.collectDependencies( session, collectRequest ).getRoot();

        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setRoot( node );

        repoSystem.resolveDependencies( session, dependencyRequest  );

        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        node.accept( nlg );
        System.out.println( nlg.getClassPath() );
    }
```

So once you have initialized the repository system and created a
session, the general pattern is to create some request object, call its
setters to configure the request, do the operation and evaluate the
result object.

Since "all theory is grey", we maintain some runnable
[examples and demos](https://github.com/apache/maven-resolver/tree/master/maven-resolver-demos) among
our sources. These examples provide a more extensive demonstration of
Resolver and its use, so what are you waiting for?