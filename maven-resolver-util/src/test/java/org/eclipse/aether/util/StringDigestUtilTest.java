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
package org.eclipse.aether.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import org.junit.Test;

public class StringDigestUtilTest {
    @Test
    public void sha1Simple() {
        assertThat(StringDigestUtil.sha1(null), is("da39a3ee5e6b4b0d3255bfef95601890afd80709"));
        assertThat(StringDigestUtil.sha1(""), is("da39a3ee5e6b4b0d3255bfef95601890afd80709"));
        assertThat(StringDigestUtil.sha1("something"), is("1af17e73721dbe0c40011b82ed4bb1a7dbe3ce29"));
        assertThat(StringDigestUtil.sha1().update(null).digest(), is("da39a3ee5e6b4b0d3255bfef95601890afd80709"));
        assertThat(StringDigestUtil.sha1().update("").digest(), is("da39a3ee5e6b4b0d3255bfef95601890afd80709"));
        assertThat(
                StringDigestUtil.sha1().update("something").digest(), is("1af17e73721dbe0c40011b82ed4bb1a7dbe3ce29"));
        assertThat(
                StringDigestUtil.sha1().update("some").update("thing").digest(),
                is("1af17e73721dbe0c40011b82ed4bb1a7dbe3ce29"));
    }

    @Test
    public void sha1Manual() {
        assertThat(new StringDigestUtil("SHA-1").digest(), is("da39a3ee5e6b4b0d3255bfef95601890afd80709"));
        assertThat(new StringDigestUtil("SHA-1").update("").digest(), is("da39a3ee5e6b4b0d3255bfef95601890afd80709"));
        assertThat(
                new StringDigestUtil("SHA-1").update("something").digest(),
                is("1af17e73721dbe0c40011b82ed4bb1a7dbe3ce29"));
        assertThat(new StringDigestUtil("SHA-1").update(null).digest(), is("da39a3ee5e6b4b0d3255bfef95601890afd80709"));
        assertThat(new StringDigestUtil("SHA-1").update("").digest(), is("da39a3ee5e6b4b0d3255bfef95601890afd80709"));
        assertThat(
                new StringDigestUtil("SHA-1").update("something").digest(),
                is("1af17e73721dbe0c40011b82ed4bb1a7dbe3ce29"));
        assertThat(
                new StringDigestUtil("SHA-1").update("some").update("thing").digest(),
                is("1af17e73721dbe0c40011b82ed4bb1a7dbe3ce29"));
    }

    @Test
    public void md5Manual() {
        assertThat(new StringDigestUtil("MD5").digest(), is("d41d8cd98f00b204e9800998ecf8427e"));
        assertThat(new StringDigestUtil("MD5").update("").digest(), is("d41d8cd98f00b204e9800998ecf8427e"));
        assertThat(new StringDigestUtil("MD5").update("something").digest(), is("437b930db84b8079c2dd804a71936b5f"));
        assertThat(new StringDigestUtil("MD5").update(null).digest(), is("d41d8cd98f00b204e9800998ecf8427e"));
        assertThat(new StringDigestUtil("MD5").update("").digest(), is("d41d8cd98f00b204e9800998ecf8427e"));
        assertThat(new StringDigestUtil("MD5").update("something").digest(), is("437b930db84b8079c2dd804a71936b5f"));
        assertThat(
                new StringDigestUtil("MD5").update("some").update("thing").digest(),
                is("437b930db84b8079c2dd804a71936b5f"));
    }

    @Test
    public void unsupportedAlg() {
        try {
            new StringDigestUtil("FOO-BAR");
            fail("StringDigestUtil should throw");
        } catch (IllegalStateException e) {
            // good
        }
    }
}
