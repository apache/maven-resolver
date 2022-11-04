package org.eclipse.aether.named;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.eclipse.aether.named.support.Retry.retry;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

/**
 * UT for {@link org.eclipse.aether.named.support.Retry}.
 */
public class RetryTest
{
    private static final long RETRY_SLEEP_MILLIS = 250L;

    @Rule
    public TestName testName = new TestName();

    @Test
    public void happy() throws InterruptedException
    {
        LongAdder retries = new LongAdder();
        String result = retry( 1L, TimeUnit.SECONDS, RETRY_SLEEP_MILLIS, () -> { retries.increment(); return "happy"; }, null, "notHappy" );
        assertThat( result, equalTo( "happy" ) );
        assertThat( retries.sum(), equalTo( 1L ) );
    }

    @Test
    public void notHappy() throws InterruptedException
    {
        LongAdder retries = new LongAdder();
        String result = retry( 1L, TimeUnit.SECONDS, RETRY_SLEEP_MILLIS, () -> { retries.increment(); return null; }, null, "notHappy" );
        assertThat( result, equalTo( "notHappy" ) );
        assertThat( retries.sum(), greaterThan( 1L ) );
    }

    @Test
    public void happyAfterSomeTime() throws InterruptedException
    {
        LongAdder retries = new LongAdder();
        String result = retry( 1L, TimeUnit.SECONDS, RETRY_SLEEP_MILLIS, () -> { retries.increment(); return retries.sum() == 2 ? "got it" : null; }, null, "notHappy" );
        assertThat( result, equalTo( "got it" ) );
        assertThat( retries.sum(), equalTo( 2L ) );
    }

    @Test
    public void happyFirstAttempt() throws InterruptedException
    {
        LongAdder retries = new LongAdder();
        String result = retry( 5, RETRY_SLEEP_MILLIS, () -> { retries.increment(); return "happy"; }, null, "notHappy" );
        assertThat( result, equalTo( "happy" ) );
        assertThat( retries.sum(), equalTo( 1L ) );
    }

    @Test
    public void notHappyAnyAttempt() throws InterruptedException
    {
        LongAdder retries = new LongAdder();
        String result = retry( 5, RETRY_SLEEP_MILLIS, () -> { retries.increment(); return null; }, null, "notHappy" );
        assertThat( result, equalTo( "notHappy" ) );
        assertThat( retries.sum(), equalTo( 5L ) );
    }

    @Test
    public void happyAfterSomeAttempt() throws InterruptedException
    {
        LongAdder retries = new LongAdder();
        String result = retry( 5, RETRY_SLEEP_MILLIS, () -> { retries.increment(); return retries.sum() == 3 ? "got it" : null; }, null, "notHappy" );
        assertThat( result, equalTo( "got it" ) );
        assertThat( retries.sum(), equalTo( 3L ) );
    }
}
