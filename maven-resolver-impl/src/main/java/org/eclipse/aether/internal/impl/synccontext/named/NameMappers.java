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
package org.eclipse.aether.internal.impl.synccontext.named;

/**
 * As end-user "mappers" are actually configurations/compositions and are constructed from several NameMapper
 * implementations, this helper class constructing them. This class also holds "names" used by service locator and
 * Guice/Sisu as well. Ideally, name mapper you want should exist here, constructing name mappers should not be
 * needed (unless some very specific case or testing).
 *
 * @since 1.9.4
 */
public final class NameMappers {
    public static final String STATIC_NAME = "static";

    public static final String GAV_NAME = "gav";

    public static final String FILE_GAV_NAME = "file-gav";

    public static final String FILE_HGAV_NAME = "file-hgav";

    /**
     * @since 1.9.25
     */
    public static final String GAECV_NAME = "gaecv";

    /**
     * @since 1.9.25
     */
    public static final String FILE_GAECV_NAME = "file-gaecv";

    /**
     * @since 1.9.25
     */
    public static final String FILE_HGAECV_NAME = "file-hgaecv";

    /**
     * @since 1.9.6
     */
    public static final String FILE_STATIC_NAME = "file-static";

    public static final String DISCRIMINATING_NAME = "discriminating";

    public static NameMapper staticNameMapper() {
        return new StaticNameMapper();
    }

    public static NameMapper gavNameMapper() {
        return gavNameMapper(false);
    }

    public static NameMapper gavNameMapper(boolean fileSystemFriendly) {
        if (fileSystemFriendly) {
            return new GAVNameMapper(true, "artifact~", ".lock", "metadata~", ".lock", "~");
        } else {
            return new GAVNameMapper(false, "artifact:", "", "metadata:", "", ":");
        }
    }

    /**
     * @since 1.9.25
     */
    public static NameMapper gaecvNameMapper() {
        return gaecvNameMapper(false);
    }

    /**
     * @since 1.9.25
     */
    public static NameMapper gaecvNameMapper(boolean fileSystemFriendly) {
        if (fileSystemFriendly) {
            return new GAECVNameMapper(true, "artifact~", ".lock", "metadata~", ".lock", "~");
        } else {
            return new GAECVNameMapper(false, "artifact:", "", "metadata:", "", ":");
        }
    }

    public static NameMapper fileGavNameMapper() {
        return new BasedirNameMapper(gavNameMapper(true));
    }

    /**
     * @since 1.9.25
     */
    public static NameMapper fileGaecvNameMapper() {
        return new BasedirNameMapper(gaecvNameMapper(true));
    }

    /**
     * @since 1.9.6
     */
    public static NameMapper fileStaticNameMapper() {
        return new BasedirNameMapper(new StaticNameMapper());
    }

    public static NameMapper fileHashingGavNameMapper() {
        return new BasedirNameMapper(new HashingNameMapper(gavNameMapper(false)));
    }

    /**
     * @since 1.9.25
     */
    public static NameMapper fileHashingGaecvNameMapper() {
        return new BasedirNameMapper(new HashingNameMapper(gaecvNameMapper(false)));
    }

    public static NameMapper discriminatingNameMapper() {
        return new DiscriminatingNameMapper(gavNameMapper(false));
    }
}
