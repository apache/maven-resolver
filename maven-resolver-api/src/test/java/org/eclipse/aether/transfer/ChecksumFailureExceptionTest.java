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
package org.eclipse.aether.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChecksumFailureExceptionTest {

    private static final String ESC = "\u001B";

    private static final String DEL = "\u007F";

    @Test
    void mismatchMessageCarriesValues() {
        ChecksumFailureException e = ChecksumFailureException.mismatch("cafebabe", "PROVIDED", "deadbeef");
        assertTrue(e.getMessage().contains("cafebabe"));
        assertTrue(e.getMessage().contains("PROVIDED"));
        assertTrue(e.getMessage().contains("deadbeef"));
        assertEquals("cafebabe", e.getExpected());
        assertEquals("PROVIDED", e.getExpectedKind());
        assertEquals("deadbeef", e.getActual());
        assertTrue(e.isRetryWorthy());
    }

    @Test
    void mismatchMessageEscapesControlCharacters() {
        // remote-supplied checksum values reach WARN logs via this message; terminal escapes must be neutralized
        String hostileExpected = "abc" + ESC + "[2K\rdef";
        String hostileActual = "actual" + DEL;
        ChecksumFailureException e = ChecksumFailureException.mismatchDetail(
                "de" + ESC + "tail", hostileExpected, "PROVIDED", hostileActual);
        String message = e.getMessage();
        assertFalse(message.contains(ESC), "raw ESC must not survive into the message");
        assertFalse(message.contains("\r"), "raw CR must not survive into the message");
        assertFalse(message.contains(DEL), "raw DEL must not survive into the message");
        assertTrue(message.contains("abc\\u001B[2K\\u000Ddef"));
        assertTrue(message.contains("actual\\u007F"));
        assertTrue(message.contains("de\\u001Btail"));
        // the accessors keep returning the raw values for programmatic consumers
        assertEquals(hostileExpected, e.getExpected());
        assertEquals(hostileActual, e.getActual());
        assertTrue(e.isRetryWorthy());
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedMismatchConstructorEscapesControlCharacters() {
        ChecksumFailureException e = new ChecksumFailureException("bad" + ESC + "[1A", "PROVIDED", "actual");
        assertFalse(e.getMessage().contains(ESC));
        assertTrue(e.getMessage().contains("bad\\u001B[1A"));
        assertEquals("bad" + ESC + "[1A", e.getExpected());
    }
}
