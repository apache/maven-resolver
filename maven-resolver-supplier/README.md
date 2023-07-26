# Maven Resolver Supplier

This simple module serves the purpose to "bootstrap" resolver when there is no desire to use Eclipse SISU. It provides
one simple class `org.eclipse.aether.supplier.RepositorySystemSupplier` that implements `Supplier<RepositorySystem>`
and supplies ready-to-use `RepositorySystem` instances.

The supplier class is written in such way, to allow easy customization if needed: just extend the class and override
method one need (all methods are protected).

By default, "full resolver experience" is provided:
* for connector, the connector-basic is added
* for transport the two transport-file and transport-http implementations are added

Consumer/User of this module **must provide SLF4J backend**. Resolver uses `slf4j-api` for logging purposes, but this 
module does NOT provide any backend for it. It is the consumer/user obligation to provide one at runtime.
