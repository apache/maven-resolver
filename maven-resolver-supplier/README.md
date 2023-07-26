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

# Maven Resolver Supplier

This simple module serves the purpose to "bootstrap" resolver when there is no desire to use Eclipse SISU. It provides
one simple class `org.eclipse.aether.supplier.RepositorySystemSupplier` that implements `Supplier<RepositorySystem>`
and supplies ready-to-use `RepositorySystem` instances.

The supplier class is written in such way, to allow easy customization if needed: just extend the class and override
method one need (all methods are protected).

Consumer/User of this module **must provide SLF4J backend**. Resolver uses `slf4j-api` for logging purposes, but this 
module does NOT provide any backend for it. It is the consumer/user obligation to provide one at runtime.

By default, "full resolver experience" is provided:
* for connector, the connector-basic is provided
* for transport the two transport-file and transport-http implementations are provided. If Wagon is needed, add
  transport-wagon as dependency, and customize `RepositorySystemSupplier` to include it. This makes it available, but
  NOT used yet! To use it, you still need to configure resolver to favor Wagon over native HTTP.

# Resolver configuration

The supplier will provide only a "vanilla" instance. To configure resolver, use session user (or 
configuration) properties, when constructing session. All the configuration options are available as 
[listed here](https://maven.apache.org/resolver/configuration.html).
