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
 * Filter that is able to tell does artifact have expected checksums or not. Most notably, artifacts like
 * different kind of signatures will not have checksums.
 *
 * @since 1.8.0
 */
public interface ArtifactChecksumFilter
{
    /**
     * Tells whether given artifact have checksums according to current layout or not. If it returns {@code false},
     * then layout configured checksums will be expected: on upload they will be calculated and deployed along
     * artifact, on download they will be retrieved and validated.
     *
     * If it returns {@code true} the given artifacts will have checksums omitted: on upload they will not be
     * calculated and deployed, and on download they will be not retrieved nor validated.
     *
     * Typical case to return {@code true} (to omit checksums) is for GPG signatures, that are already a "sub-artifact"
     * of some main artifact (ie. a JAR), and they can be validated by some other means.
     */
    boolean omitChecksumsFor( Artifact artifact );
}
