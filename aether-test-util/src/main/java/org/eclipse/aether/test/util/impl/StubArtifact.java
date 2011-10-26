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
package org.eclipse.aether.test.util.impl;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.aether.artifact.Artifact;

/**
 */
public class StubArtifact
    implements Artifact
{

    private final String groupId;

    private final String artifactId;

    private final String version;

    private final String extension;

    private final String classifier;

    private File file;

    private final Map<String, String> properties;

    public StubArtifact( String coords, Map<String, String> properties )
    {
        Pattern p = Pattern.compile( "([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)" );
        Matcher m = p.matcher( coords );
        if ( !m.matches() )
        {
            throw new IllegalArgumentException( "Bad artifact coordinates"
                + ", expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>" );
        }
        groupId = m.group( 1 );
        artifactId = m.group( 2 );
        extension = get( m.group( 4 ), "jar" );
        classifier = get( m.group( 6 ), "" );
        version = m.group( 7 );
        file = null;
        this.properties = properties;
    }
    
    public StubArtifact( String coords )
    {
        this( coords, Collections.<String, String>emptyMap());
    }

    private static String get( String value, String defaultValue )
    {
        return ( value == null || value.length() <= 0 ) ? defaultValue : value;
    }

    public StubArtifact( String groupId, String artifactId, String classifier, String extension, String version, Map<String, String> properties )
    {
        this.groupId = emptify( groupId );
        this.artifactId = emptify( artifactId );
        this.classifier = emptify( classifier );
        this.extension = emptify( extension );
        this.version = emptify( version );
        this.file = null;
        this.properties = properties != null ? properties : Collections.<String, String>emptyMap();
    }
    
    public StubArtifact( String groupId, String artifactId, String classifier, String extension, String version )
    {
        this( groupId, artifactId, classifier, extension, version, Collections.<String, String>emptyMap());
    }

    private static String emptify( String str )
    {
        return ( str == null ) ? "" : str;
    }

    public String getGroupId()
    {
        return groupId;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public String getVersion()
    {
        return version;
    }

    public Artifact setVersion( String version )
    {
        if ( getVersion().equals( version ) )
        {
            return this;
        }
        
        return new StubArtifact( getGroupId(), getArtifactId(), getClassifier(), getExtension(), version );
    }

    public String getBaseVersion()
    {
        return version;
    }

    public boolean isSnapshot()
    {
        return getVersion().endsWith( "SNAPSHOT" );
    }

    public String getClassifier()
    {
        return classifier;
    }

    public String getExtension()
    {
        return extension;
    }

    public File getFile()
    {
        return file;
    }

    public StubArtifact setFile( File file )
    {
        if ( this.file != null && this.file.equals( file )) 
        {
            return this;
        }
        return new StubArtifact( groupId, artifactId, classifier, extension, version, properties ).internalSetFile( file );
    }

    protected StubArtifact internalSetFile( File file )
    {
        this.file = file;
        return this;
    }

    public String getProperty( String key, String defaultValue )
    {
        String value = properties.get( key );
        return ( value != null ) ? value : defaultValue;
    }

    public Map<String, String> getProperties()
    {
        return Collections.unmodifiableMap( properties );
    }

    public Artifact setProperties( Map<String, String> properties )
    {
        if ( properties.equals( getProperties() ) )
        {
            return this;
        }
        else
        {
            return new StubArtifact( groupId, artifactId, classifier, extension, version, properties );
        }
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( obj == this )
        {
            return true;
        }
        else if ( !( obj instanceof Artifact ) )
        {
            return false;
        }

        Artifact that = (Artifact) obj;

        return getArtifactId().equals( that.getArtifactId() ) && getGroupId().equals( that.getGroupId() )
            && getVersion().equals( that.getVersion() ) && getExtension().equals( that.getExtension() )
            && getClassifier().equals( that.getClassifier() ) && eq( getFile(), that.getFile() )
            && getProperties().equals( that.getProperties() );
    }

    private static <T> boolean eq( T s1, T s2 )
    {
        return s1 != null ? s1.equals( s2 ) : s2 == null;
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + getGroupId().hashCode();
        hash = hash * 31 + getArtifactId().hashCode();
        hash = hash * 31 + getExtension().hashCode();
        hash = hash * 31 + getClassifier().hashCode();
        hash = hash * 31 + getVersion().hashCode();
        hash = hash * 31 + hash( getFile() );
        return hash;
    }

    private static int hash( Object obj )
    {
        return ( obj != null ) ? obj.hashCode() : 0;
    }

    @Override
    public String toString()
    {
        return String.format( "%s:%s:%s:%s", getGroupId(), getArtifactId(), getExtension(), getVersion() );
    }

}
