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
package org.eclipse.aether.named.ipc;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;

/**
 * Socket factory.
 *
 * @since 2.0.1
 */
public enum SocketFamily {
    inet,
    unix;

    public ServerSocketChannel openServerSocket() throws IOException {
        switch (this) {
            case inet:
                return ServerSocketChannel.open().bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            case unix:
                return ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(null, 0);
            default:
                throw new IllegalStateException();
        }
    }

    public static SocketAddress fromString(String str) {
        if (str.startsWith("inet:")) {
            String s = str.substring("inet:".length());
            int ic = s.lastIndexOf(':');
            String ia = s.substring(0, ic);
            int is = ia.indexOf('/');
            String h = ia.substring(0, is);
            String a = ia.substring(is + 1);
            String p = s.substring(ic + 1);
            InetAddress addr;
            if ("<unresolved>".equals(a)) {
                return InetSocketAddress.createUnresolved(h, Integer.parseInt(p));
            } else {
                if (a.indexOf('.') > 0) {
                    String[] as = a.split("\\.");
                    if (as.length != 4) {
                        throw new IllegalArgumentException("Unsupported socket address: '" + str + "'");
                    }
                    byte[] ab = new byte[4];
                    for (int i = 0; i < 4; i++) {
                        ab[i] = (byte) Integer.parseInt(as[i]);
                    }
                    try {
                        addr = InetAddress.getByAddress(h.isEmpty() ? null : h, ab);
                    } catch (UnknownHostException e) {
                        throw new IllegalArgumentException("Unsupported address: " + str, e);
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported address: " + str);
                }
                return new InetSocketAddress(addr, Integer.parseInt(p));
            }
        } else if (str.startsWith("unix:")) {
            return UnixDomainSocketAddress.of(str.substring("unix:".length()));
        } else {
            throw new IllegalArgumentException("Unsupported socket address: '" + str + "'");
        }
    }

    public static String toString(SocketAddress address) {
        switch (familyOf(address)) {
            case inet:
                InetSocketAddress isa = (InetSocketAddress) address;
                String host = isa.getHostString();
                InetAddress addr = isa.getAddress();
                int port = isa.getPort();
                String formatted;
                if (addr == null) {
                    formatted = host + "/<unresolved>";
                } else {
                    formatted = addr.toString();
                    if (addr instanceof Inet6Address) {
                        int i = formatted.lastIndexOf("/");
                        formatted = formatted.substring(0, i + 1) + "[" + formatted.substring(i + 1) + "]";
                    }
                }
                return "inet:" + formatted + ":" + port;
            case unix:
                return "unix:" + address;
            default:
                throw new IllegalArgumentException("Unsupported socket address: '" + address + "'");
        }
    }

    public static SocketFamily familyOf(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            return SocketFamily.inet;
        } else if ("java.net.UnixDomainSocketAddress".equals(address.getClass().getName())) {
            return SocketFamily.unix;
        } else {
            throw new IllegalArgumentException("Unsupported socket address '" + address + "'");
        }
    }
}
