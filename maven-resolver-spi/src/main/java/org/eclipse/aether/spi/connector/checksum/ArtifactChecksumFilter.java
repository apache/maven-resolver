package org.eclipse.aether.spi.connector.checksum;

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

import org.eclipse.aether.artifact.Artifact;

/**
 * Filter that is able to tell does artifact should have expected checksums or not. Most notably, artifacts like
 * different kind of signatures will not have checksums.
 *
 * @since 1.8.0
 */
public interface ArtifactChecksumFilter
{
    /**
     * Returns {@code true} if the given artifact does not have expected checksums. Hence, the artifact checksums will
     * not be validated during transport. If {@code false} returned, the artifact will have its checksums fetched and
     * validated.
     */
    boolean omitChecksumsFor( Artifact artifact );
}
