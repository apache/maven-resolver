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
package org.eclipse.aether.util.repository;

import java.util.function.Function;

import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class RepositoryIdHelperTest {
    @Test
    void idToPathSegment() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(s -> false);
        session.setCache(new DefaultRepositoryCache()); // session has cache set
        Function<ArtifactRepository, String> safeId = RepositoryIdHelper::idToPathSegment;

        RemoteRepository good = new RemoteRepository.Builder("good", "default", "https://somewhere.com").build();
        RemoteRepository bad = new RemoteRepository.Builder("bad/id", "default", "https://somewhere.com").build();

        String goodId = good.getId();
        String goodFixedId = safeId.apply(good);
        assertEquals(goodId, goodFixedId);

        String badId = bad.getId();
        String badFixedId = safeId.apply(bad);
        assertNotEquals(badId, badFixedId);
        assertEquals("bad-SLASH-id", badFixedId);
    }
}
