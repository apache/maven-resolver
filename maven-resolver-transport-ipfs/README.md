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

# IPFS transport

This transport uses IPFS MFS and IPNS to implement remote storage.

Valid remote repository URLs: `ipfs:/namespace[/subpath]`

Namespace ideally is reverse domain, for example `org.apache` or `org.apache.maven`, but can be also
CID or IPNS entry (without `/ipfs/` or `/ipns/` prefixes).

Subpath may be added, to adjust actual path within IPFS MFS.

Ideally, reverse domain should be used and same named key should exist on the node as well.

By default, transport connects to IPFS node running on localhost. To make it connect to some other node
set property `aether.transport.ipfs.multiaddr` (property obey repository ID suffix as well).

Default full workflow:

* assume `org.apache` namespace (and assume key exists on node as well)
* assume remote repository URL is `ipfs:/org.apache/repository`
* the IPNS entry for `org.apache` is resolved/refreshed (resolves to CID)
* the (possibly new/changed) CID is copied to MFS as `/$filePrefix/$namespace`, in this case MFS path for NS is `/publish/org.apache`
* PUTs and GETs happens against `/$filePrefix/$namespace` or `/$filePrefix/$namespace/$namespacePrefix` (depending is there namespace prefix or not), in this case MFS path for repository root is `/publish/org.apache/repository/`
* on session end IPNS entry for `org.apache` is published (with/changed CID)

