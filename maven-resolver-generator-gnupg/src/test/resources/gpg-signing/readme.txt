GnuPG Signing Test data

(do not worry, they are generated for test case only)

Files:
- artifact.txt this is the "payload" to sign
- artifact.txt.asc this is signed "payload" and verified with GnuPG CLI 2.4.3 on Fedora 39
- gpg-secret.key is a GnuPG exported secret key (ed25519) w/ secret subkey (cv25519)


Verification results:

$ gpg --verbose --verify artifact.txt.asc artifact.txt
gpg: enabled compatibility flags:
gpg: armor header: Version: BCPG v1.77.00
gpg: Signature made 2024-02-23T12:45:51 CET
gpg:                using EDDSA key 6D27BDA430672EC700BA7DBD0A32C01AE8785B6E
gpg: using pgp trust model
gpg: Good signature from "Tamas Cservenak Signer Test Key <cstamas@apache.org>" [ultimate]
gpg: binary signature, digest algorithm SHA512, key algorithm ed25519
$

GnuPG version:

$ gpg --version
gpg (GnuPG) 2.4.3
libgcrypt 1.10.2-unknown
Copyright (C) 2023 g10 Code GmbH
License GNU GPL-3.0-or-later <https://gnu.org/licenses/gpl.html>
This is free software: you are free to change and redistribute it.
There is NO WARRANTY, to the extent permitted by law.

Home: /home/cstamas/.gnupg
Supported algorithms:
Pubkey: RSA, ELG, DSA, ECDH, ECDSA, EDDSA
Cipher: IDEA, 3DES, CAST5, BLOWFISH, AES, AES192, AES256, TWOFISH,
        CAMELLIA128, CAMELLIA192, CAMELLIA256
Hash: SHA1, RIPEMD160, SHA256, SHA384, SHA512, SHA224
Compression: Uncompressed, ZIP, ZLIB, BZIP2
$
