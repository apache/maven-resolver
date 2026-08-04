<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Maven Resolver URL Transport

Special Transport with limited HTTP capabilities. The main use case of this transport is outside of
Maven, in apps that are Java 8+ and integrate Resolver only for **consumption purposes**.
Before this transport, the only option was to either bump Java level to 11 and use JDK transport, or to use
the heavyweight Apache HttpClient transport, which is not always desirable.

Supported features:
* Implemented using `java.net.HttpURLConnection` class
* HTTP 1.1 support, only for GET and HEAD methods
* HTTP redirects (max 5)
* HTTP gzip and deflate compression support
* HTTP Basic authentication (w/ preemptive support)
* HTTP proxy support (w/ Basic proxy authentication)
* HTTP auth caching (lowers "known to be needed" HTTP round-trips)
* Smart checksums (extracts checksums from response headers, potentially halves the HTTP round-trips)
* Timeout for connection and request

This transport is not a fully functional transport, and should be handled as such. It is quite usable in
artifact consumption scenarios, but it is not suitable for artifact deployment.