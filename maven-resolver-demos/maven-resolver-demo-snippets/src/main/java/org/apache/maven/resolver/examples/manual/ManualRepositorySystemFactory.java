package org.apache.maven.resolver.examples.manual;

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

import org.apache.maven.resolver.RepositorySystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory for repository system instances that employs Maven Artifact Resolver's built-in service locator
 * infrastructure to wire up the system's components.
 */
public class ManualRepositorySystemFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger( ManualRepositorySystemFactory.class );

    public static RepositorySystem newRepositorySystem()
    {
        //FIXME
        return null;
    }

}
