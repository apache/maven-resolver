# Maven Resolver Supplier

This simple module serves the purpose to "bootstrap" resolver when there is no desire to use Eclipse SISU. It provides
one simple class `org.eclipse.aether.supplier.RepositorySystemSupplier` that implements `Supplier<RepositorySystem>`
and supplies ready-to-use `RepositorySystem` instances.

## Things to be aware of

User must provide SLF4J backend. Resolver uses `slf4j-api` for logging purposes, but this module does NOT provide any 
backend for it. It is the consumer/user of this module that has to provide one.
