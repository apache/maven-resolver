package org.eclipse.aether.internal.impl.synccontext.named;

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

/**
 * As end-user "mappers" are actually configurations/compositions, are constructed from several NameMapper
 * implementations, this helper class provides and are constructing them.
 *
 * @since 1.9.4
 */
public final class NameMappers
{
    public static final String STATIC_NAME = "static";

    public static final String GAV_NAME = "gav";

    public static final String FILE_GAV_NAME = "file-gav";

    public static final String FILE_HGAV_NAME = "file-hgav";

    public static final String DISCRIMINATING_NAME = "discriminating";

    public static NameMapper staticNameMapper()
    {
        return new StaticNameMapper();
    }

    public static NameMapper gavNameMapper()
    {
        return GAVNameMapper.gav();
    }

    public static NameMapper fileGavNameMapper()
    {
        return new BasedirNameMapper( GAVNameMapper.fileGav() );
    }

    public static NameMapper fileHashingGavNameMapper()
    {
        return new BasedirNameMapper( new HashingNameMapper( GAVNameMapper.gav() ) );
    }

    public static NameMapper discriminatingNameMapper()
    {
        return new DiscriminatingNameMapper( GAVNameMapper.gav() );
    }
}
