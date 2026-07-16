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

This page lists known issues related to various transporters.

## The `jdk` (Java HttpClient) Transporter

Given this transporter uses the Java HttpClient (available since Java 11), it is the user's best interest
to use latest patch version of Java, as HttpClient is getting bugfixes regularly.

### Known issues:

* Does not properly support `aether.transport.http.requestTimeout` configuration prior Java 26, see [JDK-8208693](https://bugs.openjdk.org/browse/JDK-8208693)
* No TLS Proxy support, see [here](https://dev.to/kdrakon/httpclient-can-t-connect-to-a-tls-proxy-118a)
* No SOCKS proxy support, see [JDK-8214516](https://bugs.openjdk.org/browse/JDK-8214516)
* In case of Proxy TLS tunneling, to enable Proxy Basic authentication, one must use `jdk.http.auth.tunneling.disabledScheme` 
  Java System Property, see [here](https://docs.oracle.com/en/java/javase/21/core/java-networking.html#GUID-801380C1-4300-4ED8-A390-3C39135267CD). Java versions since 8u111 have this property set to value "Basic" making HTTP 
  Basic authentication disabled, see [here](https://www.oracle.com/java/technologies/javase/8u111-relnotes.html). To 
  enable HTTP Basic authentication for Proxy TLS tunneling, one must set `jdk.http.auth.tunneling.disabledScheme` to 
  empty string, e.g. by adding `-Djdk.http.auth.tunneling.disabledScheme=""` JVM argument.
* Preemptive basic authentication is only supported on Java 16 and below and on Java 24 and above, see [JDK-8326949](https://bugs.openjdk.org/browse/JDK-8326949).
* HTTP/2 GOAWAY frames incorrectly handled in Java < 17.0.17, Java 18 till Java < 21.0.8 and Java 22 till Java < 24, see [JDK-8335181](https://bugs.openjdk.org/browse/JDK-8335181).
* HTTP/2 GOAWAY does not lead to proper connection shutdown, see [JDK-8385131](https://bugs.openjdk.org/browse/JDK-8385131).
* HTTP/3 is only supported with [Java 26 and above](https://inside.java/2025/10/22/http3-support/).

Maven 4 uses this transport by default for HTTP(S) protocol.

## The `apache` (Apache HttpClient) Transporter

Transporter based on Apache HttpClient 4.

To use this transporter in Maven 4, you need to specify `-Dmaven.resolver.transport=apache` user property.

### Known issues:

* Does neither support HTTP/2 nor HTTP/3

## The `jetty` (Jetty HttpClient) Transporter

Transporter based on Jetty HttpClient. It requires Java 17 or above.

In Maven 4 this transport is not available by default (is not bundled). To use it, 
you need to add `org.apache.maven.resolver.transport:transport-http-jetty` artifact with its runtime dependencies to
`/lib` directory of Maven. Once added to core classpath, it will take over the role of default transport.

### Known issues:

* Leveraging HTTP/3 with Jetty suffers from [poor performance due to usage of the native Quiche Rust library]
  (https://github.com/jetty/jetty.project/discussions/13469#discussioncomment-14125855). This situation is 
  probably improving with Jetty 13 (which is supposed to be shipping with a Java QUIC implementation).
* When HTTP/3 is configured there is [no fallback to lower versions]
  (https://github.com/jetty/jetty.project/issues/15423). The request will just time out.
