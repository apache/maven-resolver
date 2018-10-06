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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.aether.artifact.Artifact;

/**
 * Can transform a file while installing/deploying
 * 
 * @author Robert Scholte
 * @since 1.3.0
 */
public interface FileTransformer
{
    /**
     * Transform the target location  
     * 
     * @param artifact the original artifact
     * @return the transformed artifact
     */
    Artifact transformArtifact( Artifact artifact );
    
    /**
     * Transform the data
     * 
     * @param file the file with the original data
     * @return the transformed data
     */
    InputStream transformData( File file ) throws IOException;
}
