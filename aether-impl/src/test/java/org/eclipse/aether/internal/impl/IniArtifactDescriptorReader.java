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
package org.eclipse.aether.internal.impl;

import org.eclipse.aether.impl.ArtifactDescriptorReader;

/**
 */
public class IniArtifactDescriptorReader
    extends org.eclipse.aether.internal.test.util.IniArtifactDescriptorReader
    implements ArtifactDescriptorReader
{

    /**
     * Use the given prefix to load the artifact descriptions.
     */
    public IniArtifactDescriptorReader( String prefix )
    {
        super( prefix );
    }

}
