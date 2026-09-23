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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IpcClientForkedTest {

    @Test
    @Timeout(30)
    void forkedServerDiagnosticsAreWrittenToLog(@TempDir Path tempDir) throws Exception {
        Path logPath = tempDir.resolve("log");
        IpcClient client = new IpcClient(tempDir.resolve("repository"), logPath, null);
        try {
            client.ensureInitialized();

            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            String logOutput = "";
            while (System.nanoTime() < deadline) {
                List<Path> logFiles;
                try (var paths = Files.list(logPath)) {
                    logFiles = paths.toList();
                }
                if (!logFiles.isEmpty()) {
                    logOutput = Files.readString(logFiles.get(0), StandardCharsets.UTF_8);
                    if (logOutput.contains("IpcServer started at")) {
                        break;
                    }
                }
                Thread.sleep(10);
            }

            assertTrue(
                    logOutput.contains("IpcServer started at"),
                    "expected forked server startup diagnostics in log, but was: " + logOutput);
        } finally {
            try {
                if (client.initialized) {
                    client.stopServer();
                }
            } finally {
                client.close();
            }
        }
    }
}
