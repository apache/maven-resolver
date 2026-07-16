client-store generated via
> keytool -genkey -alias localhost-client -keypass client-pwd -keystore client-store -storepass client-pwd -validity 4096 -dname "cn=localhost, ou=None, L=Seattle, ST=Washington, o=ExampleOrg, c=US" -keyalg RSA

server-store generated via
> keytool -genkey -alias localhost-server -keypass server-pwd -keystore server-store -storepass server-pwd -validity 4096 -dname "cn=localhost, ou=None, L=Seattle, ST=Washington, o=ExampleOrg, c=US" -keyalg RSA

The server key has to have name "localhost" otherwise the hostname matching will fail (as the server is running on localhost as well).
Both certificates are self-signed i.e. have themselves as issuer and contain both public and private keys!
Although the latter is not necessary for trust stores it won't do any harm here.
