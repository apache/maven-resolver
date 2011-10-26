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
package org.eclipse.aether.impl;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

public class StubArtifactDescriptorReader
    implements ArtifactDescriptorReader
{

    public ArtifactDescriptorResult readArtifactDescriptor( RepositorySystemSession session,
                                                            ArtifactDescriptorRequest request )
        throws ArtifactDescriptorException
    {
        ArtifactDescriptorResult result = new ArtifactDescriptorResult( request );
        return result;
    }

}
