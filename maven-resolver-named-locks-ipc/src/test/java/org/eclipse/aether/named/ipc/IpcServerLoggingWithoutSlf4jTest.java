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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * IpcServer.error()/info()/debug() route to SLF4J whenever {@code forkedProcess} is {@code false}, but
 * {@code forkedProcess} is a single static flag that {@link IpcServer#run()} resets to {@code false} in its
 * {@code finally} block as soon as the accept loop exits, including during the same JVM's shutdown sequence
 * where other code paths (e.g. a lock-acquire callback racing the server's own close()) still call these
 * methods. In the real forked server process, slf4j-api is never on the classpath (it is a "provided"
 * dependency, and {@link IpcClient}'s forked classpath only ever contains this module's own jar) so those
 * calls crash with {@link NoClassDefFoundError} instead of falling back to the forked-process stdout path.
 *
 * <p>This loads the real, compiled {@code IpcServer} class through an isolated classloader that mirrors the
 * forked process classpath exactly: slf4j classes are unresolvable, everything else resolves normally, and
 * {@code forkedProcess} starts at its default value of {@code false} (as it is after a shutdown reset) since
 * {@code main()} was never called on this fresh class instance.
 */
class IpcServerLoggingWithoutSlf4jTest {

    private static final String IPC_PACKAGE = "org.eclipse.aether.named.ipc.";

    private static Class<?> loadIpcServerWithoutSlf4j() throws ClassNotFoundException {
        CodeSource codeSource = IpcServer.class.getProtectionDomain().getCodeSource();
        URL classesUrl = codeSource.getLocation();
        ClassLoader blockingParent = new ClassLoader(IpcServer.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.slf4j.")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        ClassLoader isolated = new URLClassLoader(new URL[] {classesUrl}, blockingParent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        if (name.startsWith("org.slf4j.")) {
                            throw new ClassNotFoundException(name);
                        }
                        c = name.startsWith(IPC_PACKAGE) ? findClass(name) : super.loadClass(name, false);
                    }
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                }
            }
        };
        return Class.forName(IpcServer.class.getName(), true, isolated);
    }

    private static Method errorMethod(Class<?> ipcServerClass) throws NoSuchMethodException {
        Method method = ipcServerClass.getDeclaredMethod("error", String.class, Throwable.class);
        method.setAccessible(true);
        return method;
    }

    @Test
    void errorFallsBackToStdoutWhenForkedProcessFlagIsCorrectlySet() throws Exception {
        Class<?> ipcServerClass = loadIpcServerWithoutSlf4j();
        Field forkedProcess = ipcServerClass.getDeclaredField("forkedProcess");
        forkedProcess.setAccessible(true);
        forkedProcess.setBoolean(null, true);

        assertDoesNotThrow(() -> errorMethod(ipcServerClass).invoke(null, "sanity check", new IOException("boom")));
    }

    @Test
    void errorFallsBackToStdoutWhenForkedProcessFlagHasBeenResetDuringShutdown() throws Exception {
        // forkedProcess defaults to false on this freshly loaded class, exactly as it is in the real forked
        // process once IpcServer.run()'s finally block has reset it while shutting down - the exact state a
        // lock-acquire callback (IpcServer.sendAcquireResponse(), whose catch block calls error() without an
        // "if (!closing)" guard, unlike every other call site in the class) can still observe.
        Class<?> ipcServerClass = loadIpcServerWithoutSlf4j();

        assertDoesNotThrow(
                () -> errorMethod(ipcServerClass).invoke(null, "Error writing lock response", new IOException("boom")));
    }
}
