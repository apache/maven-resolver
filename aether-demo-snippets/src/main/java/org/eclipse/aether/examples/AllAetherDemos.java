/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.examples;

/**
 * Runs all demos at once.
 */
public class AllAetherDemos
{

    public static void main( String[] args )
        throws Exception
    {
        FindAvailableVersions.main( args );
        FindNewestVersion.main( args );
        GetDirectDependencies.main( args );
        GetDependencyTree.main( args );
        GetDependencyHierarchy.main( args );
        ResolveArtifact.main( args );
        ResolveTransitiveDependencies.main( args );
        InstallArtifacts.main( args );
        DeployArtifacts.main( args );
    }

}
