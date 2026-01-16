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
package org.eclipse.aether.transport.ipfs;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.ipfs.api.IPFS;
import io.ipfs.api.KeyInfo;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Simple helper that exposes basic IPFS related methods.
 */
@SuppressWarnings("rawtypes")
public class IpfsNamespacePublisher {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final IPFS ipfs;
    private final String nsRoot;
    private final String root;
    private final String namespace;
    private final String publishIpnsKeyName;
    private final boolean publishIpnsKeyCreate;

    public IpfsNamespacePublisher(
            IPFS ipfs,
            String namespace,
            String filesPrefix,
            String namespacePrefix,
            String publishIpnsKeyName,
            boolean publishIpnsKeyCreate) {
        this.ipfs = requireNonNull(ipfs);
        this.namespace = requireNonNull(namespace);
        this.publishIpnsKeyName = requireNonNull(publishIpnsKeyName);
        this.publishIpnsKeyCreate = publishIpnsKeyCreate;

        this.nsRoot = URI.create("ipfs:///")
                .resolve(filesPrefix + "/")
                .resolve(namespace + "/")
                .normalize()
                .getPath();
        this.root = namespacePrefix.isBlank()
                ? this.nsRoot
                : URI.create("ipfs:///")
                        .resolve(filesPrefix + "/")
                        .resolve(namespace + "/")
                        .resolve(namespacePrefix + "/")
                        .normalize()
                        .getPath();
    }

    public boolean refreshNamespace() throws IOException {
        logger.info("Refreshing IPNS {} at {}...", namespace, nsRoot);
        Optional<KeyInfo> keyInfo = getOrCreateKey(publishIpnsKeyName, publishIpnsKeyCreate);
        if (keyInfo.isPresent()) {
            try {
                Multihash namespaceCid = Multihash.decode(ipfs.name.resolve(keyInfo.orElseThrow().id));
                ipfs.files.cp("/ipfs/" + namespaceCid.toBase58(), nsRoot, true);
                ipfs.pin.add(namespaceCid);
                logger.info("Refreshed IPNS {} at {}...", namespace, nsRoot);
                return true;
            } catch (Exception e) {
                // not yet published?; ignore
                logger.debug("Could not refresh IPNS {}", keyInfo.orElseThrow().id);
            }
        } else {
            logger.info("Not refreshed: key '{}' not available and not allowed to create it", publishIpnsKeyName);
        }
        return false;
    }

    public boolean publishNamespace() throws IOException {
        logger.info("Publishing IPNS {} at {}...", namespace, nsRoot);
        Optional<Multihash> cido = getFilesPathCid(nsRoot);
        if (cido.isPresent()) {
            Multihash cid = cido.orElseThrow();
            Optional<KeyInfo> keyInfo = getOrCreateKey(publishIpnsKeyName, publishIpnsKeyCreate);
            if (keyInfo.isPresent()) {
                ipfs.pin.add(cid);
                Map publish = ipfs.name.publish(cid, Optional.of(keyInfo.orElseThrow().name));
                logger.info("Published IPNS {} (pointing to {})", publish.get("Name"), publish.get("Value"));
                return true;
            } else {
                logger.info("Not published: key '{}' not available nor allowed to create it", publishIpnsKeyName);
            }
        }
        return false;
    }

    public Map stat(String relPath) throws IOException {
        try {
            return ipfs.files.stat(root + relPath);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException && e.getMessage().contains("\"Message\":\"file does not exist\"")) {
                throw new ResourceNotFoundException("Not found");
            }
            throw e;
        }
    }

    public long size(String relPath) throws IOException {
        return Long.parseLong(String.valueOf(stat(relPath).get("Size")));
    }

    public Multihash peek(String relPath) throws IOException {
        return Multihash.decode((String) stat(relPath).get("Hash"));
    }

    public InputStream get(String relPath) throws IOException {
        return ipfs.catStream(peek(relPath));
    }

    public void put(String relPath, InputStream inputStream) throws IOException {
        ipfs.files.write(root + relPath, new NamedStreamable.InputStreamWrapper(inputStream), true, true);
    }

    private Optional<Multihash> getFilesPathCid(String path) {
        try {
            Map stat = ipfs.files.stat(path);
            return Optional.of(Multihash.decode((String) stat.get("Hash")));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<KeyInfo> getOrCreateKey(String keyName, boolean create) throws IOException {
        Optional<KeyInfo> keyInfoOptional = ipfs.key.list().stream()
                .filter(k -> Objects.equals(keyName, k.name))
                .findAny();
        if (create && keyInfoOptional.isEmpty()) {
            keyInfoOptional = Optional.of(ipfs.key.gen(keyName, Optional.empty(), Optional.empty()));
        }
        return keyInfoOptional;
    }
}
