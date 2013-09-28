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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.VersionScheme;

/**
 * Creates a dependency graph from a text description. <h2>Definition</h2> Each (non-empty) line in the input defines
 * one node of the resulting graph:
 * 
 * <pre>
 * line      ::= (indent? ("(null)" | node | reference))? comment?
 * comment   ::= "#" rest-of-line
 * indent    ::= "|  "*  ("+" | "\\") "- "
 * reference ::= "^" id
 * node      ::= coords (range)? space (scope("<" premanagedScope)?)? space "optional"? space ("relocations=" coords ("," coords)*)? ("(" id ")")?
 * coords    ::= groupId ":" artifactId (":" extension (":" classifier)?)? ":" version
 * </pre>
 * 
 * The special token {@code (null)} may be used to indicate an "empty" root node with no dependency.
 * <p>
 * If {@code indent} is empty, the line defines the root node. Only one root node may be defined. The level is
 * calculated by the distance from the beginning of the line. One level is three characters of indentation.
 * <p>
 * The {@code ^id} syntax allows to reuse a previously built node to share common sub graphs among different parent
 * nodes.
 * <h2>Example</h2>
 * 
 * <pre>
 * gid:aid:ver
 * +- gid:aid2:ver scope
 * |  \- gid:aid3:ver        (id1)    # assign id for reference below
 * +- gid:aid4:ext:ver scope
 * \- ^id1                            # reuse previous node
 * </pre>
 * 
 * <h2>Multiple definitions in one resource</h2>
 * <p>
 * By using {@link #parseMultiResource(String)}, definitions divided by a line beginning with "---" can be read from the
 * same resource. The rest of the line is ignored.
 * <h2>Substitutions</h2>
 * <p>
 * You may define substitutions (see {@link #setSubstitutions(String...)},
 * {@link #DependencyGraphParser(String, Collection)}). Every '%s' in the definition will be substituted by the next
 * String in the defined substitutions.
 * <h3>Example</h3>
 * 
 * <pre>
 * parser.setSubstitutions( &quot;foo&quot;, &quot;bar&quot; );
 * String def = &quot;gid:%s:ext:ver\n&quot; + &quot;+- gid:%s:ext:ver&quot;;
 * </pre>
 * 
 * The first node will have "foo" as its artifact id, the second node (child to the first) will have "bar" as its
 * artifact id.
 */
public class DependencyGraphParser
{

    private final VersionScheme versionScheme;

    private final String prefix;

    private Collection<String> substitutions;

    /**
     * Create a parser with the given prefix and the given substitution strings.
     * 
     * @see DependencyGraphParser#parseResource(String)
     */
    public DependencyGraphParser( String prefix, Collection<String> substitutions )
    {
        this.prefix = prefix;
        this.substitutions = substitutions;
        versionScheme = new TestVersionScheme();
    }

    /**
     * Create a parser with the given prefix.
     * 
     * @see DependencyGraphParser#parseResource(String)
     */
    public DependencyGraphParser( String prefix )
    {
        this( prefix, Collections.<String> emptyList() );
    }

    /**
     * Create a parser with an empty prefix.
     */
    public DependencyGraphParser()
    {
        this( "" );
    }

    /**
     * Parse the given graph definition.
     */
    public DependencyNode parseLiteral( String dependencyGraph )
        throws IOException
    {
        BufferedReader reader = new BufferedReader( new StringReader( dependencyGraph ) );
        DependencyNode node = parse( reader );
        reader.close();
        return node;
    }

    /**
     * Parse the graph definition read from the given classpath resource. If a prefix is set, this method will load the
     * resource from 'prefix + resource'.
     */
    public DependencyNode parseResource( String resource )
        throws IOException
    {
        URL res = this.getClass().getClassLoader().getResource( prefix + resource );
        if ( res == null )
        {
            throw new IOException( "Could not find classpath resource " + prefix + resource );
        }
        return parse( res );
    }

    /**
     * Parse multiple graphs in one resource, divided by "---".
     */
    public List<DependencyNode> parseMultiResource( String resource )
        throws IOException
    {
        URL res = this.getClass().getClassLoader().getResource( prefix + resource );
        if ( res == null )
        {
            throw new IOException( "Could not find classpath resource " + prefix + resource );
        }

        BufferedReader reader = new BufferedReader( new InputStreamReader( res.openStream(), "UTF-8" ) );

        List<DependencyNode> ret = new ArrayList<DependencyNode>();
        DependencyNode root = null;
        while ( ( root = parse( reader ) ) != null )
        {
            ret.add( root );
        }
        return ret;
    }

    /**
     * Parse the graph definition read from the given URL.
     */
    public DependencyNode parse( URL resource )
        throws IOException
    {
        InputStream stream = null;
        try
        {
            stream = resource.openStream();
            return parse( new BufferedReader( new InputStreamReader( stream, "UTF-8" ) ) );
        }
        finally
        {
            if ( stream != null )
            {
                stream.close();
            }
        }
    }

    private DependencyNode parse( BufferedReader in )
        throws IOException
    {
        Iterator<String> substitutionIterator = ( substitutions != null ) ? substitutions.iterator() : null;

        String line = null;

        DependencyNode root = null;
        DependencyNode node = null;
        int prevLevel = 0;

        Map<String, DependencyNode> nodes = new HashMap<String, DependencyNode>();
        LinkedList<DependencyNode> stack = new LinkedList<DependencyNode>();
        boolean isRootNode = true;

        while ( ( line = in.readLine() ) != null )
        {
            line = cutComment( line );

            if ( isEmpty( line ) )
            {
                // skip empty line
                continue;
            }

            if ( isEOFMarker( line ) )
            {
                // stop parsing
                break;
            }

            while ( line.contains( "%s" ) )
            {
                if ( !substitutionIterator.hasNext() )
                {
                    throw new IllegalArgumentException( "not enough substitutions to fill placeholders" );
                }
                line = line.replaceFirst( "%s", substitutionIterator.next() );
            }

            LineContext ctx = createContext( line );
            if ( prevLevel < ctx.getLevel() )
            {
                // previous node is new parent
                stack.add( node );
            }

            // get to real parent
            while ( prevLevel > ctx.getLevel() )
            {
                stack.removeLast();
                prevLevel -= 1;
            }

            prevLevel = ctx.getLevel();

            if ( ctx.getDefinition() != null && ctx.getDefinition().reference != null )
            {
                String reference = ctx.getDefinition().reference;
                DependencyNode child = nodes.get( reference );
                if ( child == null )
                {
                    throw new IllegalArgumentException( "undefined reference " + reference );
                }
                node.getChildren().add( child );
            }
            else
            {

                node = build( isRootNode ? null : stack.getLast(), ctx, isRootNode );

                if ( isRootNode )
                {
                    root = node;
                    isRootNode = false;
                }

                if ( ctx.getDefinition() != null && ctx.getDefinition().id != null )
                {
                    nodes.put( ctx.getDefinition().id, node );
                }
            }
        }

        return root;
    }

    private boolean isEOFMarker( String line )
    {
        return line.startsWith( "---" );
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

    private DependencyNode build( DependencyNode parent, LineContext ctx, boolean isRoot )
    {
        NodeDefinition def = ctx.getDefinition();
        if ( !isRoot && parent == null )
        {
            throw new IllegalArgumentException( "dangling node: " + def );
        }
        else if ( ctx.getLevel() == 0 && parent != null )
        {
            throw new IllegalArgumentException( "inconsistent leveling (parent for level 0?): " + def );
        }

        DefaultDependencyNode node;
        if ( def != null )
        {
            DefaultArtifact artifact = new DefaultArtifact( def.coords, def.properties );
            Dependency dependency = new Dependency( artifact, def.scope, def.optional );
            node = new DefaultDependencyNode( dependency );
            int managedBits = 0;
            if ( def.premanagedScope != null )
            {
                managedBits |= DependencyNode.MANAGED_SCOPE;
                node.setData( "premanaged.scope", def.premanagedScope );
            }
            if ( def.premanagedVersion != null )
            {
                managedBits |= DependencyNode.MANAGED_VERSION;
                node.setData( "premanaged.version", def.premanagedVersion );
            }
            node.setManagedBits( managedBits );
            if ( def.relocations != null )
            {
                List<Artifact> relocations = new ArrayList<Artifact>();
                for ( String relocation : def.relocations )
                {
                    relocations.add( new DefaultArtifact( relocation ) );
                }
                node.setRelocations( relocations );
            }
            try
            {
                node.setVersion( versionScheme.parseVersion( artifact.getVersion() ) );
                node.setVersionConstraint( versionScheme.parseVersionConstraint( def.range != null ? def.range
                                : artifact.getVersion() ) );
            }
            catch ( InvalidVersionSpecificationException e )
            {
                throw new IllegalArgumentException( "bad version: " + e.getMessage(), e );
            }
        }
        else
        {
            node = new DefaultDependencyNode( (Dependency) null );
        }

        if ( parent != null )
        {
            parent.getChildren().add( node );
        }

        return node;
    }

    public String dump( DependencyNode root )
    {
        StringBuilder ret = new StringBuilder();

        List<NodeEntry> entries = new ArrayList<NodeEntry>();

        addNode( root, 0, entries );

        for ( NodeEntry nodeEntry : entries )
        {
            char[] level = new char[( nodeEntry.getLevel() * 3 )];
            Arrays.fill( level, ' ' );

            if ( level.length != 0 )
            {
                level[level.length - 3] = '+';
                level[level.length - 2] = '-';
            }

            String definition = nodeEntry.getDefinition();

            ret.append( level ).append( definition ).append( "\n" );
        }

        return ret.toString();

    }

    private void addNode( DependencyNode root, int level, List<NodeEntry> entries )
    {

        NodeEntry entry = new NodeEntry();
        Dependency dependency = root.getDependency();
        StringBuilder defBuilder = new StringBuilder();
        if ( dependency == null )
        {
            defBuilder.append( "(null)" );
        }
        else
        {
            Artifact artifact = dependency.getArtifact();

            defBuilder.append( artifact.getGroupId() ).append( ":" ).append( artifact.getArtifactId() ).append( ":" ).append( artifact.getExtension() ).append( ":" ).append( artifact.getVersion() );
            if ( dependency.getScope() != null && ( !"".equals( dependency.getScope() ) ) )
            {
                defBuilder.append( ":" ).append( dependency.getScope() );
            }

            Map<String, String> properties = artifact.getProperties();
            if ( !( properties == null || properties.isEmpty() ) )
            {
                for ( Map.Entry<String, String> prop : properties.entrySet() )
                {
                    defBuilder.append( ";" ).append( prop.getKey() ).append( "=" ).append( prop.getValue() );
                }
            }
        }

        entry.setDefinition( defBuilder.toString() );
        entry.setLevel( level++ );

        entries.add( entry );

        for ( DependencyNode node : root.getChildren() )
        {
            addNode( node, level, entries );
        }

    }

    class NodeEntry
    {
        int level;

        String definition;

        Map<String, String> properties;

        public int getLevel()
        {
            return level;
        }

        public void setLevel( int level )
        {
            this.level = level;
        }

        public String getDefinition()
        {
            return definition;
        }

        public void setDefinition( String definition )
        {
            this.definition = definition;
        }

        public Map<String, String> getProperties()
        {
            return properties;
        }

        public void setProperties( Map<String, String> properties )
        {
            this.properties = properties;
        }
    }

    private static LineContext createContext( String line )
    {
        LineContext ctx = new LineContext();
        String definition;

        String[] split = line.split( "- " );
        if ( split.length == 1 ) // root
        {
            ctx.setLevel( 0 );
            definition = split[0];
        }
        else
        {
            ctx.setLevel( (int) Math.ceil( (double) split[0].length() / (double) 3 ) );
            definition = split[1];
        }

        if ( "(null)".equalsIgnoreCase( definition ) )
        {
            return ctx;
        }

        ctx.setDefinition( new NodeDefinition( definition ) );

        return ctx;
    }

    static class LineContext
    {
        NodeDefinition definition;

        int level;

        public NodeDefinition getDefinition()
        {
            return definition;
        }

        public void setDefinition( NodeDefinition definition )
        {
            this.definition = definition;
        }

        public int getLevel()
        {
            return level;
        }

        public void setLevel( int level )
        {
            this.level = level;
        }
    }

    public Collection<String> getSubstitutions()
    {
        return substitutions;
    }

    public void setSubstitutions( Collection<String> substitutions )
    {
        this.substitutions = substitutions;
    }

    public void setSubstitutions( String... substitutions )
    {
        setSubstitutions( Arrays.asList( substitutions ) );
    }

}
