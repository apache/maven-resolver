# Transporter Known Issues
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

## The `jdk` (Java HttpClient) Transport

Given this transporter uses the Java HttpClient (available since Java 11), it is in user best interest
to use latest patch version of Java, as HttpClient is getting bugfixes regularly.

Known issues:
* Does not properly support `aether.transport.http.requestTimeout` configuration prior Java 26, see [JDK-8208693](https://bugs.openjdk.org/browse/JDK-8208693)
* No TLS Proxy support, see [here](https://dev.to/kdrakon/httpclient-can-t-connect-to-a-tls-proxy-118a)
* In case of Proxy TLS tunneling, to enable Proxy Basic authentication, one must use `jdk.http.auth.tunneling.disabledScheme` 
  Java System Property, see [here](https://docs.oracle.com/en/java/javase/21/core/java-networking.html#GUID-801380C1-4300-4ED8-A390-3C39135267CD). Java versions since 8u111 have this property set to value "Basic" making HTTP 
  Basic authentication disabled, see [here](https://www.oracle.com/java/technologies/javase/8u111-relnotes.html). To 
  enable HTTP Basic authentication for Proxy TLS tunneling, one must set `jdk.http.auth.tunneling.disabledScheme` to 
  empty string, e.g. by adding `-Djdk.http.auth.tunneling.disabledScheme=""` JVM argument.

Maven 4 uses this transport by default for HTTP(S) protocol.

## The `apache` (Apache HttpClient) Transport

Transporter based on Apache HttpClient.

To use this transporter in Maven 4, you need to specify `-Dmaven.resolver.transport=apache` user property.

## The `jetty` (Jetty HttpClient) Transport

Transporter based on Jetty HttpClient. 

In Maven 4 this transport is not available by default (is not bundled). To use it, 
you need to add `org.apache.maven.resolver.transport:transport-http-jetty` artifact with its runtime dependencies to
`/lib` directory of Maven. Once added to core classpath, it will take over the role of default transport.
