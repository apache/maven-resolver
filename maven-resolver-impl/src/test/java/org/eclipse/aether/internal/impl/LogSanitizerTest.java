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
package org.eclipse.aether.internal.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class LogSanitizerTest {

    private static final char ESC = '\u001B';

    private static final char DEL = '\u007F';

    @Test
    void nullPassesThrough() {
        assertNull(LogSanitizer.sanitize(null));
    }

    @Test
    void cleanStringReturnsSameInstance() {
        String clean = "org.apache.maven.resolver:artifact:jar:1.0 from https://repo.example.com/";
        assertSame(clean, LogSanitizer.sanitize(clean));
    }

    @Test
    void newlineAndTabArePreserved() {
        String value = "line1\nline2\tcolumn";
        assertSame(value, LogSanitizer.sanitize(value));
    }

    @Test
    void escapeSequenceIsNeutralized() {
        // ESC[2K erases the current terminal line, CR returns the cursor: together they cancel a WARNING line
        assertEquals("abc\\u001B[2K\\u000Ddef", LogSanitizer.sanitize("abc" + ESC + "[2K\rdef"));
    }

    @Test
    void carriageReturnIsEscaped() {
        assertEquals("evil\\u000D[INFO] BUILD SUCCESS", LogSanitizer.sanitize("evil\r[INFO] BUILD SUCCESS"));
    }

    @Test
    void deleteAndLowControlsAreEscaped() {
        assertEquals("a\\u007Fb\\u0000c\\u0008d", LogSanitizer.sanitize("a" + DEL + "b\u0000c\bd"));
    }

    @Test
    void sanitizedOutputContainsNoRawControls() {
        StringBuilder hostile = new StringBuilder("x");
        for (char c = 0; c < 0x20; c++) {
            hostile.append(c);
        }
        hostile.append(DEL).append('y');
        String sanitized = LogSanitizer.sanitize(hostile.toString());
        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            assertFalse((c < 0x20 && c != '\n' && c != '\t') || c == DEL, "raw control char at index " + i);
        }
    }
}
