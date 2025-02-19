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

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.SocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.FileLock;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.eclipse.aether.named.ipc.IpcMessages.REQUEST_ACQUIRE;
import static org.eclipse.aether.named.ipc.IpcMessages.REQUEST_CLOSE;
import static org.eclipse.aether.named.ipc.IpcMessages.REQUEST_CONTEXT;
import static org.eclipse.aether.named.ipc.IpcMessages.REQUEST_STOP;
import static org.eclipse.aether.named.ipc.IpcMessages.RESPONSE_ACQUIRE;
import static org.eclipse.aether.named.ipc.IpcMessages.RESPONSE_CLOSE;
import static org.eclipse.aether.named.ipc.IpcMessages.RESPONSE_CONTEXT;
import static org.eclipse.aether.named.ipc.IpcMessages.RESPONSE_STOP;

/**
 * Client side implementation.
 * The client instance is bound to a given maven repository.
 *
 * @since 2.0.1
 */
public class IpcClient {

    static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win");

    volatile boolean initialized;
    Path lockPath;
    Path logPath;
    Path syncPath;
    SocketChannel socket;
    DataOutputStream output;
    DataInputStream input;
    Thread receiver;
    AtomicInteger requestId = new AtomicInteger();
    Map<Integer, CompletableFuture<List<String>>> responses = new ConcurrentHashMap<>();

    IpcClient(Path lockPath, Path logPath, Path syncPath) {
        this.lockPath = lockPath;
        this.logPath = logPath;
        this.syncPath = syncPath;
    }

    void ensureInitialized() throws IOException {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    socket = createClient();
                    ByteChannel wrapper = new ByteChannelWrapper(socket);
                    input = new DataInputStream(Channels.newInputStream(wrapper));
                    output = new DataOutputStream(Channels.newOutputStream(wrapper));
                    receiver = new Thread(this::receive);
                    receiver.setDaemon(true);
                    receiver.start();
                    initialized = true;
                }
            }
        }
    }

    SocketChannel createClient() throws IOException {
        String familyProp = System.getProperty(IpcServer.SYSTEM_PROP_FAMILY, IpcServer.DEFAULT_FAMILY);
        SocketFamily family = familyProp != null ? SocketFamily.valueOf(familyProp) : SocketFamily.inet;

        Path lockPath = this.lockPath.toAbsolutePath().normalize();
        Path lockFile =
                lockPath.resolve(".maven-resolver-ipc-lock-" + family.name().toLowerCase(Locale.ENGLISH));
        if (!Files.isRegularFile(lockFile)) {
            if (!Files.isDirectory(lockFile.getParent())) {
                Files.createDirectories(lockFile.getParent());
            }
        }

        try (RandomAccessFile raf = new RandomAccessFile(lockFile.toFile(), "rw")) {
            try (FileLock lock = raf.getChannel().lock()) {
                String line = raf.readLine();
                if (line != null) {
                    try {
                        SocketAddress address = SocketFamily.fromString(line);
                        return SocketChannel.open(address);
                    } catch (IOException e) {
                        // ignore
                    }
                }

                ServerSocketChannel ss = family.openServerSocket();
                String tmpaddr = SocketFamily.toString(ss.getLocalAddress());
                String rand = Long.toHexString(new Random().nextLong());
                String nativeName =
                        System.getProperty(IpcServer.SYSTEM_PROP_NATIVE_NAME, IpcServer.DEFAULT_NATIVE_NAME);
                String syncCmd = IS_WINDOWS ? nativeName + ".exe" : nativeName;

                boolean debug = Boolean.parseBoolean(
                        System.getProperty(IpcServer.SYSTEM_PROP_DEBUG, Boolean.toString(IpcServer.DEFAULT_DEBUG)));
                boolean noNative = Boolean.parseBoolean(System.getProperty(
                        IpcServer.SYSTEM_PROP_NO_NATIVE, Boolean.toString(IpcServer.DEFAULT_NO_NATIVE)));
                if (!noNative) {
                    noNative = !Files.isExecutable(syncPath.resolve(syncCmd));
                }
                Closeable close;
                Path logFile = logPath.resolve("resolver-ipcsync-" + rand + ".log");
                List<String> args = new ArrayList<>();
                if (noNative) {
                    boolean noFork = Boolean.parseBoolean(System.getProperty(
                            IpcServer.SYSTEM_PROP_NO_FORK, Boolean.toString(IpcServer.DEFAULT_NO_FORK)));
                    if (noFork) {
                        IpcServer server = IpcServer.runServer(family, tmpaddr, rand);
                        close = server::close;
                    } else {
                        String javaHome = System.getenv("JAVA_HOME");
                        if (javaHome == null) {
                            javaHome = System.getProperty("java.home");
                        }
                        String javaCmd = IS_WINDOWS ? "bin\\java.exe" : "bin/java";
                        String java = Paths.get(javaHome)
                                .resolve(javaCmd)
                                .toAbsolutePath()
                                .toString();
                        args.add(java);
                        String classpath = getJarPath(getClass()) + File.pathSeparator + getJarPath(IpcServer.class);
                        args.add("-cp");
                        args.add(classpath);
                        String timeout = System.getProperty(IpcServer.SYSTEM_PROP_IDLE_TIMEOUT);
                        if (timeout != null) {
                            args.add("-D" + IpcServer.SYSTEM_PROP_IDLE_TIMEOUT + "=" + timeout);
                        }
                        args.add("-D" + IpcServer.SYSTEM_PROP_DEBUG + "=" + debug);
                        args.add(IpcServer.class.getName());
                        args.add(family.name());
                        args.add(tmpaddr);
                        args.add(rand);
                        ProcessBuilder processBuilder = new ProcessBuilder();
                        ProcessBuilder.Redirect discard = ProcessBuilder.Redirect.to(logFile.toFile());
                        Files.createDirectories(logPath);
                        Process process = processBuilder
                                .directory(lockFile.getParent().toFile())
                                .command(args)
                                .redirectOutput(discard)
                                .redirectError(discard)
                                .start();
                        close = process::destroyForcibly;
                    }
                } else {
                    args.add(syncPath.resolve(syncCmd).toString());
                    String timeout = System.getProperty(IpcServer.SYSTEM_PROP_IDLE_TIMEOUT);
                    if (timeout != null) {
                        args.add("-D" + IpcServer.SYSTEM_PROP_IDLE_TIMEOUT + "=" + timeout);
                    }
                    args.add("-D" + IpcServer.SYSTEM_PROP_DEBUG + "=" + debug);
                    args.add(family.name());
                    args.add(tmpaddr);
                    args.add(rand);
                    ProcessBuilder processBuilder = new ProcessBuilder();
                    ProcessBuilder.Redirect discard = ProcessBuilder.Redirect.to(logFile.toFile());
                    Files.createDirectories(logPath);
                    Process process = processBuilder
                            .directory(lockFile.getParent().toFile())
                            .command(args)
                            .redirectOutput(discard)
                            .redirectError(discard)
                            .start();
                    close = process::destroyForcibly;
                }

                ExecutorService es = Executors.newSingleThreadExecutor();
                Future<String[]> future = es.submit(() -> {
                    SocketChannel s = ss.accept();
                    DataInputStream dis = new DataInputStream(Channels.newInputStream(s));
                    String rand2 = dis.readUTF();
                    String addr2 = dis.readUTF();
                    return new String[] {rand2, addr2};
                });
                String[] res;
                try {
                    res = future.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    try (PrintWriter writer = new PrintWriter(new FileWriter(logFile.toFile(), true))) {
                        writer.println("Arguments:");
                        args.forEach(writer::println);
                        writer.println();
                        writer.println("Exception:");
                        e.printStackTrace(writer);
                    }
                    close.close();
                    throw e;
                } finally {
                    es.shutdownNow();
                    ss.close();
                }
                if (!Objects.equals(rand, res[0])) {
                    close.close();
                    throw new IllegalStateException("IpcServer did not respond with the correct random");
                }

                SocketAddress addr = SocketFamily.fromString(res[1]);
                SocketChannel socket = SocketChannel.open(addr);

                raf.seek(0);
                raf.writeBytes(res[1] + "\n");
                return socket;
            } catch (Exception e) {
                throw new RuntimeException("Unable to create and connect to lock server", e);
            }
        }
    }

    private String getJarPath(Class<?> clazz) {
        String classpath;
        String className = clazz.getName().replace('.', '/') + ".class";
        String url = clazz.getClassLoader().getResource(className).toString();
        if (url.startsWith("jar:")) {
            url = url.substring("jar:".length(), url.indexOf("!/"));
            if (url.startsWith("file:")) {
                classpath = url.substring("file:".length());
            } else {
                throw new IllegalStateException();
            }
        } else if (url.startsWith("file:")) {
            classpath = url.substring("file:".length(), url.indexOf(className));
        } else {
            throw new IllegalStateException();
        }
        if (IS_WINDOWS) {
            if (classpath.startsWith("/")) {
                classpath = classpath.substring(1);
            }
            classpath = classpath.replace('/', '\\');
        }

        return classpath;
    }

    void receive() {
        try {
            while (true) {
                int id = input.readInt();
                int sz = input.readInt();
                List<String> s = new ArrayList<>(sz);
                for (int i = 0; i < sz; i++) {
                    s.add(input.readUTF());
                }
                CompletableFuture<List<String>> f = responses.remove(id);
                if (f == null || s.isEmpty()) {
                    throw new IllegalStateException("Protocol error");
                }
                f.complete(s);
            }
        } catch (EOFException e) {
            // server is stopped; just quit
        } catch (Exception e) {
            close(e);
        }
    }

    List<String> send(List<String> request, long time, TimeUnit unit) throws TimeoutException, IOException {
        ensureInitialized();
        int id = requestId.incrementAndGet();
        CompletableFuture<List<String>> response = new CompletableFuture<>();
        responses.put(id, response);
        synchronized (output) {
            output.writeInt(id);
            output.writeInt(request.size());
            for (String s : request) {
                output.writeUTF(s);
            }
            output.flush();
        }
        try {
            return response.get(time, unit);
        } catch (InterruptedException e) {
            throw (IOException) new InterruptedIOException("Interrupted").initCause(e);
        } catch (ExecutionException e) {
            throw new IOException("Execution error", e);
        }
    }

    void close() {
        close(new IOException("Closing"));
    }

    synchronized void close(Throwable e) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException t) {
                e.addSuppressed(t);
            }
            socket = null;
            input = null;
            output = null;
        }
        if (receiver != null) {
            receiver.interrupt();
            try {
                receiver.join(1000);
            } catch (InterruptedException t) {
                e.addSuppressed(t);
            }
        }
        responses.values().forEach(f -> f.completeExceptionally(e));
        responses.clear();
    }

    String newContext(boolean shared, long time, TimeUnit unit) throws TimeoutException {
        RuntimeException error = new RuntimeException("Unable to create new sync context");
        for (int i = 0; i < 2; i++) {
            try {
                List<String> response = send(Arrays.asList(REQUEST_CONTEXT, Boolean.toString(shared)), time, unit);
                if (response.size() != 2 || !RESPONSE_CONTEXT.equals(response.get(0))) {
                    throw new IOException("Unexpected response: " + response);
                }
                return response.get(1);
            } catch (TimeoutException e) {
                throw e;
            } catch (Exception e) {
                close(e);
                error.addSuppressed(e);
            }
        }
        throw error;
    }

    void lock(String contextId, Collection<String> keys, long time, TimeUnit unit) throws TimeoutException {
        try {
            List<String> req = new ArrayList<>(keys.size() + 2);
            req.add(REQUEST_ACQUIRE);
            req.add(contextId);
            req.addAll(keys);
            List<String> response = send(req, time, unit);
            if (response.size() != 1 || !RESPONSE_ACQUIRE.equals(response.get(0))) {
                throw new IOException("Unexpected response: " + response);
            }
        } catch (TimeoutException e) {
            throw e;
        } catch (Exception e) {
            close(e);
            throw new RuntimeException("Unable to perform lock (contextId = " + contextId + ")", e);
        }
    }

    void unlock(String contextId) {
        try {
            List<String> response =
                    send(Arrays.asList(REQUEST_CLOSE, contextId), Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            if (response.size() != 1 || !RESPONSE_CLOSE.equals(response.get(0))) {
                throw new IOException("Unexpected response: " + response);
            }
        } catch (Exception e) {
            close(e);
            throw new RuntimeException("Unable to unlock (contextId = " + contextId + ")", e);
        }
    }

    /**
     * To be used in tests to stop server immediately. Should not be used outside of tests.
     */
    void stopServer() {
        try {
            List<String> response = send(Arrays.asList(REQUEST_STOP), Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            if (response.size() != 1 || !RESPONSE_STOP.equals(response.get(0))) {
                throw new IOException("Unexpected response: " + response);
            }
        } catch (Exception e) {
            close(e);
            throw new RuntimeException("Unable to stop server", e);
        }
    }

    @Override
    public String toString() {
        return "IpcClient{"
                + "lockPath=" + lockPath + ","
                + "syncServerPath=" + syncPath + ","
                + "address='" + getAddress() + "'}";
    }

    private String getAddress() {
        try {
            return SocketFamily.toString(socket.getLocalAddress());
        } catch (IOException e) {
            return "[not bound]";
        }
    }
}
