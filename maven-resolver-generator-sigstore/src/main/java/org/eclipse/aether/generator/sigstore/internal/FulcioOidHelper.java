/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.generator.sigstore.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.DEROctetString;

/**
 * Helper to decode Fulcio OID data, see <a
 * href="https://github.com/sigstore/fulcio/blob/main/docs/oid-info.md">Sigstore OID
 * information</a>.
 */
public class FulcioOidHelper {
    private static final String SIGSTORE_OID_ROOT = "1.3.6.1.4.1.57264";
    private static final String FULCIO_OID_ROOT = SIGSTORE_OID_ROOT + ".1";

    @Deprecated
    private static final String FULCIO_ISSUER_OID = FULCIO_OID_ROOT + ".1";

    private static final String FULCIO_ISSUER_V2_OID = FULCIO_OID_ROOT + ".8";

    public static String getIssuer(X509Certificate cert) {
        String issuerV2 = getIssuerV2(cert);
        if (issuerV2 == null) {
            return getIssuerV1(cert);
        }
        return issuerV2;
    }

    @Deprecated
    public static String getIssuerV1(X509Certificate cert) {
        return getExtensionValue(cert, FULCIO_ISSUER_OID, true);
    }

    public static String getIssuerV2(X509Certificate cert) {
        return getExtensionValue(cert, FULCIO_ISSUER_V2_OID, false);
    }

    /* Extracts the octets from an extension value and converts to utf-8 directly, it does NOT
     * account for any ASN1 encoded value. If the extension value is an ASN1 object (like an
     * ASN1 encoded string), you need to write a new extraction helper. */
    private static String getExtensionValue(X509Certificate cert, String oid, boolean rawUtf8) {
        byte[] extensionValue = cert.getExtensionValue(oid);

        if (extensionValue == null) {
            return null;
        }
        try {
            ASN1Primitive derObject = ASN1Sequence.fromByteArray(cert.getExtensionValue(oid));
            if (derObject instanceof DEROctetString) {
                DEROctetString derOctetString = (DEROctetString) derObject;
                if (rawUtf8) {
                    // this is unusual, but the octet is a raw utf8 string in fulcio land (no prefix of type)
                    // and not an ASN1 object.
                    return new String(derOctetString.getOctets(), StandardCharsets.UTF_8);
                }

                derObject = ASN1Sequence.fromByteArray(derOctetString.getOctets());
                if (derObject instanceof ASN1String) {
                    ASN1String s = (ASN1String) derObject;
                    return s.getString();
                }
            }
            throw new RuntimeException(
                    "Could not parse extension " + oid + " in certificate because it was not an octet string");
        } catch (IOException ioe) {
            throw new RuntimeException("Could not parse extension " + oid + " in certificate", ioe);
        }
    }
}
