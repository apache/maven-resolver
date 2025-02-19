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
package demo; // CHECKSTYLE_OFF: RegexpHeader

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple tool to check file locking on your OS/FS/Java combo. To use this tool, just copy it to basedir on
 * the volume you plan to use as local repository and compile and run it:
 * <ul>
 *   <li><pre>javac demo.TestNioLock.java</pre></li>
 *   <li><pre>java demo.TestNioLock test someFile 1000</pre></li>
 * </ul>
 */
public class TestNioLock {
    private static final int EC_WON = 10;

    private static final int EC_LOST = 20;

    private static final int EC_FAILED = 30;

    private static final int EC_ERROR = 100;

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 3) {
            System.out.println("demo.TestNioLock <test|perform> <file> <sleepMs>");
            System.exit(EC_ERROR);
        }

        String mode = args[0];
        Path path = Paths.get(args[1]).toAbsolutePath();
        Path latchFile = path.getParent().resolve(TestNioLock.class.getName() + ".latchFile");

        if (Files.isDirectory(path)) {
            System.out.println("The <file> cannot be directory.");
            System.exit(EC_ERROR);
        }
        if (!Files.isRegularFile(latchFile)) {
            Files.createFile(latchFile);
        }

        if ("test".equals(mode)) {
            System.out.println("Testing file locking on");
            System.out.println(
                    "  Java " + System.getProperty("java.version") + ", " + System.getProperty("java.vendor"));
            System.out.println("  OS " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                    + System.getProperty("os.arch"));

            FileStore fileStore = Files.getFileStore(path.getParent());
            System.out.println("  FS " + fileStore.name() + " " + fileStore.type());
            System.out.println();

            AtomicInteger oneResult = new AtomicInteger(-1);
            AtomicInteger twoResult = new AtomicInteger(-1);
            CountDownLatch latch = new CountDownLatch(2);
            String javaCmd = System.getProperty("java.home") + "/bin/java";

            try (FileChannel latchChannel =
                    FileChannel.open(latchFile, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                try (FileLock latchLock = latchChannel.lock(0L, 1L, false)) {
                    new Thread(() -> {
                                try {
                                    oneResult.set(new ProcessBuilder(
                                                    javaCmd, TestNioLock.class.getName(), "perform", args[1], args[2])
                                            .inheritIO()
                                            .start()
                                            .waitFor());
                                } catch (Exception e) {
                                    oneResult.set(EC_FAILED);
                                } finally {
                                    latch.countDown();
                                }
                            })
                            .start();
                    new Thread(() -> {
                                try {
                                    twoResult.set(new ProcessBuilder(
                                                    javaCmd, TestNioLock.class.getName(), "perform", args[1], args[2])
                                            .inheritIO()
                                            .start()
                                            .waitFor());
                                } catch (Exception e) {
                                    twoResult.set(EC_FAILED);
                                } finally {
                                    latch.countDown();
                                }
                            })
                            .start();

                    Thread.sleep(1000); // give them a bit of time (to both block)
                    latchLock.release();
                    latch.await();
                }
            }

            int oneExit = oneResult.get();
            int twoExit = twoResult.get();
            if ((oneExit == EC_WON && twoExit == EC_LOST) || (oneExit == EC_LOST && twoExit == EC_WON)) {
                System.out.println("OK");
                System.exit(0);
            } else {
                System.out.println("FAILED: one=" + oneExit + " two=" + twoExit);
                System.exit(EC_FAILED);
            }
        } else if ("perform".equals(mode)) {
            String processName = ManagementFactory.getRuntimeMXBean().getName();
            System.out.println(processName + " > started");
            boolean won = false;
            long sleepMs = Long.parseLong(args[2]);
            try (FileChannel latchChannel = FileChannel.open(latchFile, StandardOpenOption.READ)) {
                try (FileLock latchLock = latchChannel.lock(0L, 1L, true)) {
                    System.out.println(processName + " > latchLock acquired");
                    try (FileChannel channel = FileChannel.open(
                            path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                        try (FileLock lock = channel.tryLock(0L, 1L, false)) {
                            if (lock != null && lock.isValid() && !lock.isShared()) {
                                System.out.println(processName + " > WON");
                                won = true;
                                Thread.sleep(sleepMs);
                            } else {
                                System.out.println(processName + " > LOST");
                            }
                        }
                    }
                }
            }
            System.out.println(processName + " > ended");
            if (won) {
                System.exit(EC_WON);
            } else {
                System.exit(EC_LOST);
            }
        } else {
            System.err.println("Unknown mode: " + mode);
        }
        System.exit(EC_ERROR);
    }
}
