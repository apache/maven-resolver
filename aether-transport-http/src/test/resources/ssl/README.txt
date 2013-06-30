client-store generated via
> keytool -genkey -alias localhost -keypass client-pwd -keystore client-store -storepass client-pwd -validity 4096 -dname "cn=localhost, ou=None, L=Seattle, ST=Washington, o=ExampleOrg, c=US" -keyalg RSA

server-store generated via
> keytool -genkey -alias localhost -keypass server-pwd -keystore server-store -storepass server-pwd -validity 4096 -dname "cn=localhost, ou=None, L=Seattle, ST=Washington, o=ExampleOrg, c=US" -keyalg RSA
