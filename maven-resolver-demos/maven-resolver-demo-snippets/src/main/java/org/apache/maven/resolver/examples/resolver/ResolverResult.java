package org.apache.maven.resolver.examples.resolver;

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
import java.util.List;

import org.eclipse.aether.graph.DependencyNode;

/**
 */
public class ResolverResult
{
    private final DependencyNode root;
    private final List<File> resolvedFiles;
    private final String resolvedClassPath;
    
    public ResolverResult( DependencyNode root, List<File> resolvedFiles, String resolvedClassPath )
    {
        this.root = root;
        this.resolvedFiles = resolvedFiles;
        this.resolvedClassPath = resolvedClassPath;
    }

    public DependencyNode getRoot()
    {
        return root;
    }

    public List<File> getResolvedFiles()
    {
        return resolvedFiles;
    }

    public String getResolvedClassPath()
    {
        return resolvedClassPath;
    }
}
