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
package org.eclipse.aether.util.artifact;

import java.io.File;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.util.artifact.AbstractArtifact;

/**
 * An artifact that delegates to another artifact instance. This class serves as a base for subclasses that want to
 * carry additional data fields.
 */
public abstract class DelegatingArtifact
    extends AbstractArtifact
{

    private final Artifact delegate;

    /**
     * Creates a new artifact instance that delegates to the specified artifact.
     * 
     * @param delegate The artifact to delegate to, must not be {@code null}.
     */
    protected DelegatingArtifact( Artifact delegate )
    {
        if ( delegate == null )
        {
            throw new IllegalArgumentException( "delegate artifact not specified" );
        }
        this.delegate = delegate;
    }

    /**
     * Creates a new artifact instance that delegates to the specified artifact. Subclasses should use this hook to
     * instantiate themselves, taking along any data from the current instance that was added.
     * 
     * @param delegate The artifact to delegate to, must not be {@code null}.
     * @return The new delegating artifact, never {@code null}.
     */
    protected abstract DelegatingArtifact newInstance( Artifact delegate );

    public String getGroupId()
    {
        return delegate.getGroupId();
    }

    public String getArtifactId()
    {
        return delegate.getArtifactId();
    }

    public String getVersion()
    {
        return delegate.getVersion();
    }

    public Artifact setVersion( String version )
    {
        Artifact artifact = delegate.setVersion( version );
        if ( artifact != delegate )
        {
            return newInstance( artifact );
        }
        return this;
    }

    public String getBaseVersion()
    {
        return delegate.getBaseVersion();
    }

    public boolean isSnapshot()
    {
        return delegate.isSnapshot();
    }

    public String getClassifier()
    {
        return delegate.getClassifier();
    }

    public String getExtension()
    {
        return delegate.getExtension();
    }

    public File getFile()
    {
        return delegate.getFile();
    }

    public Artifact setFile( File file )
    {
        Artifact artifact = delegate.setFile( file );
        if ( artifact != delegate )
        {
            return newInstance( artifact );
        }
        return this;
    }

    public String getProperty( String key, String defaultValue )
    {
        return delegate.getProperty( key, defaultValue );
    }

    public Map<String, String> getProperties()
    {
        return delegate.getProperties();
    }

    public Artifact setProperties( Map<String, String> properties )
    {
        Artifact artifact = delegate.setProperties( properties );
        if ( artifact != delegate )
        {
            return newInstance( artifact );
        }
        return this;
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( obj == this )
        {
            return true;
        }

        if ( obj instanceof DelegatingArtifact )
        {
            return delegate.equals( ( (DelegatingArtifact) obj ).delegate );
        }

        return delegate.equals( obj );
    }

    @Override
    public int hashCode()
    {
        return delegate.hashCode();
    }

    @Override
    public String toString()
    {
        return delegate.toString();
    }

}
