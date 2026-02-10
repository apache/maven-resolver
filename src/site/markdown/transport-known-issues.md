# Transport Known Issues
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

This page lists known issues related to various transports.

## The `jdk`, JDK Transport

This is the default transport used in Maven 4.

Known issues:
* Does not properly support `aether.transport.http.requestTimeout` prior Java 26, see [JDK-8208693](https://bugs.openjdk.org/browse/JDK-8208693)
* No TLS proxy support, see [here](https://dev.to/kdrakon/httpclient-can-t-connect-to-a-tls-proxy-118a)
* In case of Proxy TLS tunneling, to enable Proxy authentication one must issue `jdk.http.auth.tunneling.disabledSchemes`, see [here](https://docs.oracle.com/en/java/javase/21/core/java-networking.html#GUID-801380C1-4300-4ED8-A390-3C39135267CD).

## The `apache`, Apache HttpClient Transport

To use this transport, you need to specify `-Dmaven.resolver.transport=apache` user property.

## The `jetty`, Jetty HttpClient Transport

This transport is not available by default (is not bundled with Maven). To use it, 
you need to add `org.apache.maven.resolver.transport:transport-http-jetty` artifact with its runtime dependencies to
`/lib` directory of Maven. Once added, it will become the default transport.
