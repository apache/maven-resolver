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
package org.eclipse.aether.connector.basic;

import static org.eclipse.aether.connector.basic.TestChecksumAlgorithmSelector.MD5;
import static org.eclipse.aether.connector.basic.TestChecksumAlgorithmSelector.SHA1;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.internal.test.util.TestFileProcessor;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy.ChecksumKind;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.junit.Before;
import org.junit.Test;

public class ChecksumValidatorTest {

    private static class StubChecksumPolicy implements ChecksumPolicy {

        boolean inspectAll;

        boolean tolerateFailure;

        private final ArrayList<String> callbacks = new ArrayList<>();

        private Object conclusion;

        @Override
        public boolean onChecksumMatch(String algorithm, ChecksumKind kind) {
            callbacks.add(String.format("match(%s, %s)", algorithm, kind));
            if (inspectAll) {
                if (conclusion == null) {
                    conclusion = true;
                }
                return false;
            }
            return true;
        }

        @Override
        public void onChecksumMismatch(String algorithm, ChecksumKind kind, ChecksumFailureException exception)
                throws ChecksumFailureException {
            callbacks.add(String.format("mismatch(%s, %s)", algorithm, kind));
            if (inspectAll) {
                conclusion = exception;
                return;
            }
            throw exception;
        }

        @Override
        public void onChecksumError(String algorithm, ChecksumKind kind, ChecksumFailureException exception) {
            callbacks.add(String.format(
                    "error(%s, %s, %s)", algorithm, kind, exception.getCause().getMessage()));
        }

        @Override
        public void onNoMoreChecksums() throws ChecksumFailureException {
            callbacks.add(String.format("noMore()"));
            if (conclusion instanceof ChecksumFailureException) {
                throw (ChecksumFailureException) conclusion;
            } else if (!Boolean.TRUE.equals(conclusion)) {
                throw new ChecksumFailureException("no checksums");
            }
        }

        @Override
        public void onTransferRetry() {
            callbacks.add(String.format("retry()"));
        }

        @Override
        public boolean onTransferChecksumFailure(ChecksumFailureException exception) {
            callbacks.add(String.format("fail(%s)", exception.getMessage()));
            return tolerateFailure;
        }

        void assertCallbacks(String... callbacks) {
            assertEquals(Arrays.asList(callbacks), this.callbacks);
        }
    }

    private static class StubChecksumFetcher implements ChecksumValidator.ChecksumFetcher {

        HashMap<URI, Object> checksums = new HashMap<>();

        ArrayList<File> checksumFiles = new ArrayList<>();

        private final ArrayList<URI> fetchedFiles = new ArrayList<>();

        @Override
        public boolean fetchChecksum(URI remote, File local) throws Exception {
            fetchedFiles.add(remote);
            Object checksum = checksums.get(remote);
            if (checksum == null) {
                return false;
            }
            if (checksum instanceof Exception) {
                throw (Exception) checksum;
            }
            TestFileUtils.writeString(local, checksum.toString());
            checksumFiles.add(local);
            return true;
        }

        void mock(String algo, Object value) {
            checksums.put(toUri(algo), value);
        }

        void assertFetchedFiles(String... algos) {
            List<URI> expected = new ArrayList<>();
            for (String algo : algos) {
                expected.add(toUri(algo));
            }
            assertEquals(expected, fetchedFiles);
        }

        private static URI toUri(String algo) {
            return newChecksum(algo).getLocation();
        }
    }

    private StubChecksumPolicy policy;

    private StubChecksumFetcher fetcher;

    private File dataFile;

    private static final TestChecksumAlgorithmSelector selector = new TestChecksumAlgorithmSelector();

    private List<ChecksumAlgorithmFactory> newChecksumAlgorithmFactories(String... factories) {
        List<ChecksumAlgorithmFactory> checksums = new ArrayList<>();
        for (String factory : factories) {
            checksums.add(selector.select(factory));
        }
        return checksums;
    }

    private static RepositoryLayout.ChecksumLocation newChecksum(String factory) {
        return RepositoryLayout.ChecksumLocation.forLocation(URI.create("file"), selector.select(factory));
    }

    private List<RepositoryLayout.ChecksumLocation> newChecksums(
            List<ChecksumAlgorithmFactory> checksumAlgorithmFactories) {
        List<RepositoryLayout.ChecksumLocation> checksums = new ArrayList<>();
        for (ChecksumAlgorithmFactory factory : checksumAlgorithmFactories) {
            checksums.add(RepositoryLayout.ChecksumLocation.forLocation(URI.create("file"), factory));
        }
        return checksums;
    }

    private ChecksumValidator newValidator(String... factories) {
        return newValidator(null, factories);
    }

    private ChecksumValidator newValidator(Map<String, String> providedChecksums, String... factories) {
        List<ChecksumAlgorithmFactory> checksumAlgorithmFactories = newChecksumAlgorithmFactories(factories);
        return new ChecksumValidator(
                dataFile,
                checksumAlgorithmFactories,
                new TestFileProcessor(),
                fetcher,
                policy,
                providedChecksums,
                newChecksums(checksumAlgorithmFactories));
    }

    private Map<String, ?> checksums(String... algoDigestPairs) {
        Map<String, Object> checksums = new LinkedHashMap<>();
        for (int i = 0; i < algoDigestPairs.length; i += 2) {
            String algo = algoDigestPairs[i];
            String digest = algoDigestPairs[i + 1];
            if (digest == null) {
                checksums.put(algo, new IOException("error"));
            } else {
                checksums.put(algo, digest);
            }
        }
        return checksums;
    }

    @Before
    public void init() throws Exception {
        dataFile = TestFileUtils.createTempFile("");
        dataFile.delete();
        policy = new StubChecksumPolicy();
        fetcher = new StubChecksumFetcher();
    }

    @Test
    public void testValidate_NullPolicy() throws Exception {
        policy = null;
        ChecksumValidator validator = newValidator(SHA1);
        validator.validate(checksums(SHA1, "ignored"), null);
        fetcher.assertFetchedFiles();
    }

    @Test
    public void testValidate_AcceptOnFirstMatch() throws Exception {
        ChecksumValidator validator = newValidator(SHA1);
        fetcher.mock(SHA1, "foo");
        validator.validate(checksums(SHA1, "foo"), null);
        fetcher.assertFetchedFiles(SHA1);
        policy.assertCallbacks("match(SHA-1, REMOTE_EXTERNAL)");
    }

    @Test
    public void testValidate_FailOnFirstMismatch() {
        ChecksumValidator validator = newValidator(SHA1);
        fetcher.mock(SHA1, "foo");
        try {
            validator.validate(checksums(SHA1, "not-foo"), null);
            fail("expected exception");
        } catch (ChecksumFailureException e) {
            assertEquals("foo", e.getExpected());
            assertEquals(ChecksumKind.REMOTE_EXTERNAL.name(), e.getExpectedKind());
            assertEquals("not-foo", e.getActual());
            assertTrue(e.isRetryWorthy());
        }
        fetcher.assertFetchedFiles(SHA1);
        policy.assertCallbacks("mismatch(SHA-1, REMOTE_EXTERNAL)");
    }

    @Test
    public void testValidate_AcceptOnEnd() throws Exception {
        policy.inspectAll = true;
        ChecksumValidator validator = newValidator(SHA1, MD5);
        fetcher.mock(SHA1, "foo");
        fetcher.mock(MD5, "bar");
        validator.validate(checksums(SHA1, "foo", MD5, "bar"), null);
        fetcher.assertFetchedFiles(SHA1, MD5);
        policy.assertCallbacks("match(SHA-1, REMOTE_EXTERNAL)", "match(MD5, REMOTE_EXTERNAL)", "noMore()");
    }

    @Test
    public void testValidate_FailOnEnd() {
        policy.inspectAll = true;
        ChecksumValidator validator = newValidator(SHA1, MD5);
        fetcher.mock(SHA1, "foo");
        fetcher.mock(MD5, "bar");
        try {
            validator.validate(checksums(SHA1, "not-foo", MD5, "bar"), null);
            fail("expected exception");
        } catch (ChecksumFailureException e) {
            assertEquals("foo", e.getExpected());
            assertEquals(ChecksumKind.REMOTE_EXTERNAL.name(), e.getExpectedKind());
            assertEquals("not-foo", e.getActual());
            assertTrue(e.isRetryWorthy());
        }
        fetcher.assertFetchedFiles(SHA1, MD5);
        policy.assertCallbacks("mismatch(SHA-1, REMOTE_EXTERNAL)", "match(MD5, REMOTE_EXTERNAL)", "noMore()");
    }

    @Test
    public void testValidate_IncludedBeforeExternal() throws Exception {
        policy.inspectAll = true;
        HashMap<String, String> provided = new HashMap<>();
        provided.put(SHA1, "foo");
        ChecksumValidator validator = newValidator(provided, SHA1, MD5);
        fetcher.mock(SHA1, "foo");
        fetcher.mock(MD5, "bar");
        validator.validate(checksums(SHA1, "foo", MD5, "bar"), checksums(SHA1, "foo", MD5, "bar"));
        fetcher.assertFetchedFiles(SHA1, MD5);
        policy.assertCallbacks(
                "match(SHA-1, PROVIDED)",
                "match(SHA-1, REMOTE_INCLUDED)",
                "match(MD5, REMOTE_INCLUDED)",
                "match(SHA-1, REMOTE_EXTERNAL)",
                "match(MD5, REMOTE_EXTERNAL)",
                "noMore()");
    }

    @Test
    public void testValidate_CaseInsensitive() throws Exception {
        policy.inspectAll = true;
        ChecksumValidator validator = newValidator(SHA1);
        fetcher.mock(SHA1, "FOO");
        validator.validate(checksums(SHA1, "foo"), checksums(SHA1, "foo"));
        policy.assertCallbacks("match(SHA-1, REMOTE_INCLUDED)", "match(SHA-1, REMOTE_EXTERNAL)", "noMore()");
    }

    @Test
    public void testValidate_MissingRemoteChecksum() throws Exception {
        ChecksumValidator validator = newValidator(SHA1, MD5);
        fetcher.mock(MD5, "bar");
        validator.validate(checksums(MD5, "bar"), null);
        fetcher.assertFetchedFiles(SHA1, MD5);
        policy.assertCallbacks("match(MD5, REMOTE_EXTERNAL)");
    }

    @Test
    public void testValidate_InaccessibleRemoteChecksum() throws Exception {
        ChecksumValidator validator = newValidator(SHA1, MD5);
        fetcher.mock(SHA1, new IOException("inaccessible"));
        fetcher.mock(MD5, "bar");
        validator.validate(checksums(MD5, "bar"), null);
        fetcher.assertFetchedFiles(SHA1, MD5);
        policy.assertCallbacks("error(SHA-1, REMOTE_EXTERNAL, inaccessible)", "match(MD5, REMOTE_EXTERNAL)");
    }

    @Test
    public void testValidate_InaccessibleLocalChecksum() throws Exception {
        ChecksumValidator validator = newValidator(SHA1, MD5);
        fetcher.mock(SHA1, "foo");
        fetcher.mock(MD5, "bar");
        validator.validate(checksums(SHA1, null, MD5, "bar"), null);
        fetcher.assertFetchedFiles(MD5);
        policy.assertCallbacks("error(SHA-1, REMOTE_EXTERNAL, error)", "match(MD5, REMOTE_EXTERNAL)");
    }

    @Test
    public void testHandle_Accept() {
        policy.tolerateFailure = true;
        ChecksumValidator validator = newValidator(SHA1);
        assertTrue(validator.handle(new ChecksumFailureException("accept")));
        policy.assertCallbacks("fail(accept)");
    }

    @Test
    public void testHandle_Reject() {
        policy.tolerateFailure = false;
        ChecksumValidator validator = newValidator(SHA1);
        assertFalse(validator.handle(new ChecksumFailureException("reject")));
        policy.assertCallbacks("fail(reject)");
    }

    @Test
    public void testRetry_ResetPolicy() {
        ChecksumValidator validator = newValidator(SHA1);
        validator.retry();
        policy.assertCallbacks("retry()");
    }

    @Test
    public void testRetry_RemoveTempFiles() throws Exception {
        ChecksumValidator validator = newValidator(SHA1);
        fetcher.mock(SHA1, "foo");
        validator.validate(checksums(SHA1, "foo"), null);
        fetcher.assertFetchedFiles(SHA1);
        assertEquals(1, fetcher.checksumFiles.size());
        for (File file : fetcher.checksumFiles) {
            assertTrue(file.getAbsolutePath(), file.isFile());
        }
        validator.retry();
        for (File file : fetcher.checksumFiles) {
            assertFalse(file.getAbsolutePath(), file.exists());
        }
    }

    @Test
    public void testCommit_SaveChecksumFiles() throws Exception {
        policy.inspectAll = true;
        ChecksumValidator validator = newValidator(SHA1, MD5);
        fetcher.mock(MD5, "bar");
        validator.validate(checksums(SHA1, "foo", MD5, "bar"), checksums(SHA1, "foo"));
        assertEquals(1, fetcher.checksumFiles.size());
        for (File file : fetcher.checksumFiles) {
            assertTrue(file.getAbsolutePath(), file.isFile());
        }
        validator.commit();
        File checksumFile = new File(dataFile.getPath() + ".sha1");
        assertTrue(checksumFile.getAbsolutePath(), checksumFile.isFile());
        assertEquals("foo", TestFileUtils.readString(checksumFile));
        checksumFile = new File(dataFile.getPath() + ".md5");
        assertTrue(checksumFile.getAbsolutePath(), checksumFile.isFile());
        assertEquals("bar", TestFileUtils.readString(checksumFile));
        for (File file : fetcher.checksumFiles) {
            assertFalse(file.getAbsolutePath(), file.exists());
        }
    }

    @Test
    public void testClose_RemoveTempFiles() throws Exception {
        ChecksumValidator validator = newValidator(SHA1);
        fetcher.mock(SHA1, "foo");
        validator.validate(checksums(SHA1, "foo"), null);
        fetcher.assertFetchedFiles(SHA1);
        assertEquals(1, fetcher.checksumFiles.size());
        for (File file : fetcher.checksumFiles) {
            assertTrue(file.getAbsolutePath(), file.isFile());
        }
        validator.close();
        for (File file : fetcher.checksumFiles) {
            assertFalse(file.getAbsolutePath(), file.exists());
        }
    }
}
