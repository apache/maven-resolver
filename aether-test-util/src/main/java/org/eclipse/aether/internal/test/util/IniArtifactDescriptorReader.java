/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.test.util;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

/**
 * An artifact descriptor reader that gets data from a simple text file on the classpath. The data file for an artifact
 * with the coordinates {@code gid:aid:ext:ver} is expected to be named {@code gid_aid_ext_ver.ini} and can optionally
 * have some prefix. The data file can have the following sections:
 * <ul>
 * <li>relocation</li>
 * <li>dependencies</li>
 * <li>managedDependencies</li>
 * <li>repositories</li>
 * </ul>
 * The relocation and dependency sections contain artifact coordinates of the form:
 * 
 * <pre>
 * gid:aid:ver:ext[:scope][:optional]
 * </pre>
 * 
 * The dependency sections may also specify exclusions:
 * 
 * <pre>
 * -gid:aid
 * </pre>
 * 
 * A repository definition is of the form:
 * 
 * <pre>
 * id:type:url
 * </pre>
 * 
 * <h2>Example</h2>
 * 
 * <pre>
 * [relocation]
 * gid:aid:ver:ext
 * 
 * [dependencies]
 * gid:aid:ver:ext:scope
 * -exclusion:aid
 * gid:aid2:ver:ext:scope:optional
 * 
 * [managed-dependencies]
 * gid:aid2:ver2:ext:scope
 * -gid:aid
 * -gid:aid
 * 
 * [repositories]
 * id:type:file:///test-repo
 * </pre>
 */
public class IniArtifactDescriptorReader
{
    private IniArtifactDataReader reader;

    /**
     * Use the given prefix to load the artifact descriptions from the classpath.
     */
    public IniArtifactDescriptorReader( String prefix )
    {
        reader = new IniArtifactDataReader( prefix );
    }

    /**
     * Parses the resource {@code $prefix/gid_aid_ext_ver.ini} from the request artifact as an artifact description and
     * wraps it into an ArtifactDescriptorResult.
     */
    public ArtifactDescriptorResult readArtifactDescriptor( RepositorySystemSession session,
                                                            ArtifactDescriptorRequest request )
        throws ArtifactDescriptorException
    {
        ArtifactDescriptorResult result = new ArtifactDescriptorResult( request );
        for ( Artifact artifact = request.getArtifact();; )
        {
            String resourceName =
                String.format( "%s_%s_%s_%s.ini", artifact.getGroupId(), artifact.getArtifactId(),
                               artifact.getVersion(), artifact.getExtension() );
            try
            {
                ArtifactDescription data = reader.parse( resourceName );
                if ( data.getRelocation() != null )
                {
                    result.addRelocation( artifact );
                    artifact = data.getRelocation();
                }
                else
                {
                    result.setArtifact( artifact );
                    result.setDependencies( data.getDependencies() );
                    result.setManagedDependencies( data.getManagedDependencies() );
                    result.setRepositories( data.getRepositories() );
                    return result;
                }
            }
            catch ( Exception e )
            {
                throw new ArtifactDescriptorException( result, e.getMessage() );
            }
        }
    }

}
