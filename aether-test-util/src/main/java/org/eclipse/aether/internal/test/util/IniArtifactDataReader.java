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
package org.eclipse.aether.internal.test.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * @see IniArtifactDescriptorReader
 */
class IniArtifactDataReader
{

    private String prefix = "";

    /**
     * Constructs a data reader with the prefix {@code ""}.
     */
    public IniArtifactDataReader()
    {
        this( "" );
    }

    /**
     * Constructs a data reader with the given prefix.
     * 
     * @param prefix the prefix to use for loading resources from the classpath.
     */
    public IniArtifactDataReader( String prefix )
    {
        this.prefix = prefix;

    }

    /**
     * Load an artifact description from the classpath and parse it.
     */
    public ArtifactDescription parse( String resource )
        throws IOException
    {
        URL res = this.getClass().getClassLoader().getResource( prefix + resource );

        if ( res == null )
        {
            throw new IOException( "cannot find resource: " + resource );
        }
        return parse( res );
    }

    /**
     * Open the given URL and parse ist.
     */
    public ArtifactDescription parse( URL res )
        throws IOException
    {
        return parse( new InputStreamReader( res.openStream(), "UTF-8" ) );
    }

    /**
     * Parse the given String.
     */
    public ArtifactDescription parseLiteral( String description )
        throws IOException
    {
        StringReader reader = new StringReader( description );
        return parse( reader );
    }

    private enum State
    {
        NONE, RELOCATION, DEPENDENCIES, MANAGEDDEPENDENCIES, REPOSITORIES
    }

    private ArtifactDescription parse( Reader reader )
        throws IOException
    {
        String line = null;

        State state = State.NONE;

        Map<State, List<String>> sections = new HashMap<State, List<String>>();

        BufferedReader in = new BufferedReader( reader );
        try
        {
            while ( ( line = in.readLine() ) != null )
            {

                line = cutComment( line );
                if ( isEmpty( line ) )
                {
                    continue;
                }

                if ( line.startsWith( "[" ) )
                {
                    try
                    {
                        String name = line.substring( 1, line.length() - 1 );
                        name = name.replace( "-", "" ).toUpperCase( Locale.ENGLISH );
                        state = State.valueOf( name );
                        sections.put( state, new ArrayList<String>() );
                    }
                    catch ( IllegalArgumentException e )
                    {
                        throw new IOException( "unknown section: " + line );
                    }
                }
                else
                {
                    List<String> lines = sections.get( state );
                    if ( lines == null )
                    {
                        throw new IOException( "missing section: " + line );
                    }
                    lines.add( line.trim() );
                }
            }
        }
        finally
        {
            in.close();
        }

        Artifact relocation = relocation( sections.get( State.RELOCATION ) );
        List<Dependency> dependencies = dependencies( sections.get( State.DEPENDENCIES ), "compile" );
        List<Dependency> managedDependencies = dependencies( sections.get( State.MANAGEDDEPENDENCIES ), "" );
        List<RemoteRepository> repositories = repositories( sections.get( State.REPOSITORIES ) );

        ArtifactDescription description =
            new ArtifactDescription( relocation, dependencies, managedDependencies, repositories );
        return description;
    }

    private List<RemoteRepository> repositories( List<String> list )
    {
        ArrayList<RemoteRepository> ret = new ArrayList<RemoteRepository>();
        if ( list == null )
        {
            return ret;
        }
        for ( String coords : list )
        {
            String[] split = coords.split( ":", 3 );
            String id = split[0];
            String type = split[1];
            String url = split[2];

            ret.add( new RemoteRepository.Builder( id, type, url ).build() );
        }
        return ret;
    }

    private List<Dependency> dependencies( List<String> list, String defaultScope )
    {
        List<Dependency> ret = new ArrayList<Dependency>();
        if ( list == null )
        {
            return ret;
        }

        Collection<Exclusion> exclusions = new ArrayList<Exclusion>();

        Boolean optional = null;
        Artifact artifact = null;
        String scope = null;

        for ( String coords : list )
        {
            if ( coords.startsWith( "-" ) )
            {
                coords = coords.substring( 1 );
                String[] split = coords.split( ":" );
                exclusions.add( new Exclusion( split[0], split[1], "*", "*" ) );
            }
            else
            {
                if ( artifact != null )
                {
                    // commit dependency
                    Dependency dep = new Dependency( artifact, scope, optional, exclusions );
                    ret.add( dep );

                    exclusions = new ArrayList<Exclusion>();
                }

                ArtifactDefinition def = new ArtifactDefinition( coords );

                optional = def.getOptional();

                scope = "".equals( def.getScope() ) ? defaultScope : def.getScope();

                artifact =
                    new DefaultArtifact( def.getGroupId(), def.getArtifactId(), "", def.getExtension(),
                                         def.getVersion() );
            }
        }
        if ( artifact != null )
        {
            // commit dependency
            Dependency dep = new Dependency( artifact, scope, optional, exclusions );
            ret.add( dep );
        }

        return ret;
    }

    private Artifact relocation( List<String> list )
    {
        if ( list == null || list.isEmpty() )
        {
            return null;
        }
        String coords = list.get( 0 );
        ArtifactDefinition def = new ArtifactDefinition( coords );
        return new DefaultArtifact( def.getGroupId(), def.getArtifactId(), "", def.getExtension(), def.getVersion() );
    }

    private static boolean isEmpty( String line )
    {
        return line == null || line.length() == 0;
    }

    private static String cutComment( String line )
    {
        int idx = line.indexOf( '#' );

        if ( idx != -1 )
        {
            line = line.substring( 0, idx );
        }

        return line;
    }

    static class Definition
    {
        private String groupId;

        private String artifactId;

        private String extension;

        private String version;

        private String scope = "";

        private String definition;

        private String id = null;

        private String reference = null;

        private boolean optional = false;

        public Definition( String def )
        {
            this.definition = def.trim();

            if ( definition.startsWith( "(" ) )
            {
                int idx = definition.indexOf( ')' );
                this.id = definition.substring( 1, idx );
                this.definition = definition.substring( idx + 1 );
            }
            else if ( definition.startsWith( "^" ) )
            {
                this.reference = definition.substring( 1 );
                return;
            }

            String[] split = definition.split( ":" );
            if ( split.length < 4 )
            {
                throw new IllegalArgumentException( "Need definition like 'gid:aid:ext:ver[:scope]', but was: "
                    + definition );
            }
            groupId = split[0];
            artifactId = split[1];
            extension = split[2];
            version = split[3];
            if ( split.length > 4 )
            {
                scope = split[4];
            }
            if ( split.length > 5 && "true".equalsIgnoreCase( split[5] ) )
            {
                optional = true;
            }
        }

        public String getGroupId()
        {
            return groupId;
        }

        public String getArtifactId()
        {
            return artifactId;
        }

        public String getType()
        {
            return extension;
        }

        public String getVersion()
        {
            return version;
        }

        public String getScope()
        {
            return scope;
        }

        @Override
        public String toString()
        {
            return definition;
        }

        public String getId()
        {
            return id;
        }

        public String getReference()
        {
            return reference;
        }

        public boolean isReference()
        {
            return reference != null;
        }

        public boolean hasId()
        {
            return id != null;
        }

        public boolean isOptional()
        {
            return optional;
        }
    }

}
