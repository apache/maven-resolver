# Maven Resolver URL Transport

This is special Transport with limited HTTP capabilities. The main use case of this transport is outside of
Maven, but in case of other apps that are Java 8, and integrate Resolver mostly for **consumption purposes**.
Before this transport, the only option was to either up Java level to 11 and use JDK transport, or to use
the heavyweight Apache HttpClient transport, which is not always desirable.

Supported features:
* HTTP 1.1 support, only for GET and HEAD methods
* HTTP redirects (max 5)
* HTTP gzip and deflate compression support
* HTTP Basic authentication (w/ preemptive support)
* HTTP proxy support (w/ Basic proxy authentication)
* HTTP auth caching (lowers "known to needed" HTTP round-trips)
* Smart checksums (extracts checksums from response headers, potentially halves the HTTP round-trips)
* Timeout for connection and request

This transport is not a fully functional transport, and should be handled a such. It is quite usable in
artifact consumption scenarios, but it is not suitable for artifact deployment.