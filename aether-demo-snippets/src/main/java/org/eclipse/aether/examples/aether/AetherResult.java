/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.examples.aether;

import java.io.File;
import java.util.List;

import org.eclipse.aether.graph.DependencyNode;

public class AetherResult
{
    private DependencyNode root;
    private List<File> resolvedFiles;
    private String resolvedClassPath;
    
    public AetherResult( DependencyNode root, List<File> resolvedFiles, String resolvedClassPath )
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
