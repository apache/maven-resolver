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
package org.eclipse.aether.repository;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RepositoryUriUtils#toUri(String)}, focusing on Windows drive-letter handling.
 * Tests that pass plain Windows paths (with backslash or {@code X:} prefix) are
 * {@link EnabledOnOs @EnabledOnOs(OS.WINDOWS)} because on Linux {@code Paths.get("C:\\...")}
 * treats the input as a relative path (backslash is not a separator), producing a wrong URI
 * that could accidentally satisfy the drive-letter assertion.
 * Tests that pass {@code file:} URLs are safe to run on all platforms.
 */
public class RepositoryUriUtilsTest {

    /**
     * A plain Windows path with an uppercase drive letter must produce a {@code file:} URI
     * whose path starts with the drive letter, not a relative path missing the drive.
     * <p>Restricted to Windows because on Linux {@code Paths.get("C:\\...")} is a relative path.</p>
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsUppercaseDriveLetterPreserved() {
        URI uri = RepositoryUriUtils.toUri("C:\\Users\\foo\\repo");
        assertEquals("file", uri.getScheme());
        // The path component must contain the drive letter
        String path = uri.getSchemeSpecificPart();
        assertContainsDriveLetter('C', path);
    }

    /**
     * A plain Windows path with a lowercase drive letter must also be handled correctly.
     * Before the fix, only uppercase letters were detected as drive letters.
     * <p>Restricted to Windows because on Linux {@code Paths.get("c:\\...")} is a relative path.</p>
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsLowercaseDriveLetterPreserved() {
        URI uri = RepositoryUriUtils.toUri("c:\\Users\\foo\\repo");
        assertEquals("file", uri.getScheme());
        String path = uri.getSchemeSpecificPart();
        assertContainsDriveLetter('c', path);
    }

    /**
     * A Windows path with forward slashes must also be handled correctly.
     * <p>Restricted to Windows because {@code C:/...} is parsed as a URL scheme on Linux
     * ({@code "C"} would be the protocol), which takes a different code path.</p>
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsDriveLetterForwardSlash() {
        URI uri = RepositoryUriUtils.toUri("C:/Users/foo/repo");
        assertEquals("file", uri.getScheme());
        String path = uri.getSchemeSpecificPart();
        assertContainsDriveLetter('C', path);
    }

    /**
     * A {@code file:} URL with a Windows drive letter (as produced by Windows CI runners)
     * must be converted without an {@code InvalidPathException}. This was the root cause of the
     * CI failures: {@code file:/D:/a/...} was passed through {@code new File(...).toURI()} which
     * resolved against the JVM working directory when the drive letters differed.
     */
    @Test
    void fileUrlWithWindowsDriveLetter() {
        // This is the URL format that Windows GitHub Actions runners produce
        URI uri = RepositoryUriUtils.toUri("file:/C:/Users/test/repo");
        assertEquals("file", uri.getScheme());
        String path = uri.getSchemeSpecificPart();
        assertContainsDriveLetter('C', path);
    }

    /**
     * A {@code file:///} URL (three slashes, standard RFC 8089 form) with a Windows drive letter.
     */
    @Test
    void fileUrlTripleSlashWithWindowsDriveLetter() {
        URI uri = RepositoryUriUtils.toUri("file:///C:/Users/test/repo");
        assertEquals("file", uri.getScheme());
        String path = uri.getSchemeSpecificPart();
        assertContainsDriveLetter('C', path);
    }

    /**
     * A Unix absolute path must round-trip unchanged.
     */
    @Test
    void unixAbsolutePath() {
        URI uri = RepositoryUriUtils.toUri("/home/user/repo");
        assertEquals("file", uri.getScheme());
    }

    /**
     * A {@code file:} URL with a Unix path must be converted to a file: URI.
     * Note: on Windows, Paths.get(URI) for a Unix-style path will resolve against
     * the current drive, so we only assert the scheme here.
     */
    @Test
    void fileUrlUnixPath() {
        URI uri = RepositoryUriUtils.toUri("file:///home/user/repo");
        assertEquals("file", uri.getScheme());
    }

    /**
     * A non-file URL (e.g. {@code https:}) must be returned as-is.
     */
    @Test
    void httpsUrlPassthrough() {
        URI uri = RepositoryUriUtils.toUri("https://repo.example.com/maven");
        assertEquals("https", uri.getScheme());
        assertEquals("repo.example.com", uri.getHost());
    }

    /**
     * An opaque {@code file:} URL with an unencoded space (e.g. {@code file:repo space}) must not throw
     * and must resolve to an absolute path that retains the space.
     * Regression guard for the {@code URI.create()} path that rejects unencoded spaces.
     */
    @Test
    void fileUrlOpaqueWithUnencodedSpace() {
        URI uri = RepositoryUriUtils.toUri("file:repo space");
        assertEquals("file", uri.getScheme());
        // path must contain the space, not a percent-encoded form
        assertTrue(
                uri.getPath().contains(" ") || uri.getSchemeSpecificPart().contains(" "),
                "Expected a space in the resolved URI but got: " + uri);
    }

    /**
     * A hierarchical {@code file:} URL with an unencoded space (e.g. {@code file:/repo space}) must not throw
     * and must resolve to a path that retains the space.
     * Regression guard for the multi-arg {@code URI} constructor path.
     */
    @Test
    void fileUrlHierarchicalWithUnencodedSpace() {
        URI uri = RepositoryUriUtils.toUri("file:/repo space");
        assertEquals("file", uri.getScheme());
        assertTrue(uri.getPath().contains(" "), "Expected a space in the URI path but got: " + uri);
    }

    /**
     * A {@code file:} URL with a percent-encoded space (e.g. {@code file:repo%20space}) must decode the space
     * and resolve to an absolute path containing a literal space.
     */
    @Test
    void fileUrlWithPercentEncodedSpace() {
        URI uri = RepositoryUriUtils.toUri("file:repo%20space");
        assertEquals("file", uri.getScheme());
        assertTrue(uri.getPath().contains(" "), "Expected decoded space in the URI path but got: " + uri);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Asserts that {@code path} contains the given drive letter followed by a colon or slash,
     * using a locale-independent comparison.
     */
    private static void assertContainsDriveLetter(char driveLetter, String path) {
        char upper = Character.toUpperCase(driveLetter);
        // Accept both uppercase and lowercase in the URI path; the important thing is that
        // the drive letter (either case) is present and followed by ':' or '/'
        boolean found = path.indexOf(upper + ":") >= 0
                || path.indexOf(Character.toLowerCase(upper) + ":") >= 0
                || path.indexOf("/" + upper + "/") >= 0
                || path.indexOf("/" + Character.toLowerCase(upper) + "/") >= 0;
        assertEquals(true, found, "Expected drive letter '" + upper + "' in URI path but got: " + path);
    }
}
