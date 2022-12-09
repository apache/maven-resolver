package org.eclipse.aether.transform;

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

import java.io.IOException;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;

/**
 * Can transform am artifact while installing/deploying.
 * <p>
 * Important note: given transformation happens within resolver boundaries, any transformation (aside of "identity")
 * may change artifact coordinates and/or artifact content, hence if there was some computation involved BEFORE
 * transformation (typically, signing artifact or checksum/hash calculation for artifact) the transformation may render
 * all those invalid, UNLESS the transformation transform all the subordinates of transformed artifact as well.
 * 
 * @author Robert Scholte et al.
 * @since 1.9.3
 */
public interface ArtifactTransformer
{
    /**
     * Transform the target artifact for install.
     *
     * @param session the session.
     * @param artifact the original artifact.
     * @return the transformed artifact, never {@code null}.
     */
    TransformedArtifact transformInstallArtifact( RepositorySystemSession session, Artifact artifact )
            throws TransformException, IOException;

    /**
     * Transform the target artifact for deploy.
     *
     * @param session the session.
     * @param artifact the original artifact.
     * @return the transformed artifact, never {@code null}.
     */
    TransformedArtifact transformDeployArtifact( RepositorySystemSession session, Artifact artifact )
            throws TransformException, IOException;
}
