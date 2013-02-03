/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.test.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A definition of a dependency node via a single line of text.
 * 
 * @see DependencyGraphParser
 */
class NodeDefinition
{

    static final String ID = "\\(([-_a-zA-Z0-9]+)\\)";

    static final String IDREF = "\\^([-_a-zA-Z0-9]+)";

    static final String COORDS = "([^: \\(]+):([^: ]+)(?::([^: ]*)(?::([^: ]+))?)?:([^: \\[\\(<]+)";

    private static final String COORDS_NC = NodeDefinition.COORDS.replaceAll( "\\((?=\\[)", "(?:" );

    private static final String RANGE_NC = "[\\(\\[][^\\(\\)\\[\\]]+[\\)\\]]";

    static final String RANGE = "(" + RANGE_NC + ")";

    static final String SCOPE = "(?:scope\\s*=\\s*)?((?!optional)[-_a-zA-Z0-9]+)(?:<([-_a-zA-Z0-9]+))?";

    static final String OPTIONAL = "(!?optional)";

    static final String RELOCATIONS = "relocations\\s*=\\s*(" + COORDS_NC + "(?:\\s*,\\s*" + COORDS_NC + ")*)";

    static final String KEY_VAL = "(?:[-_a-zA-Z0-9]+)\\s*:\\s*(?:[-_a-zA-Z0-9]*)";

    static final String PROPS = "props\\s*=\\s*(" + KEY_VAL + "(?:\\s*,\\s*" + KEY_VAL + ")*)";

    static final String COORDSX = "(" + COORDS_NC + ")" + RANGE + "?(?:<((?:" + RANGE_NC + ")|\\S+))?";

    static final String NODE = COORDSX + "(?:\\s+" + PROPS + ")?" + "(?:\\s+" + SCOPE + ")?" + "(?:\\s+" + OPTIONAL
        + ")?" + "(?:\\s+" + RELOCATIONS + ")?" + "(?:\\s+" + ID + ")?";

    static final String LINE = "(?:" + IDREF + ")|(?:" + NODE + ")";

    private static final Pattern PATTERN = Pattern.compile( LINE );

    private final String def;

    String coords;

    Map<String, String> properties;

    String range;

    String premanagedVersion;

    String scope;

    String premanagedScope;

    Boolean optional;

    List<String> relocations;

    String id;

    String reference;

    public NodeDefinition( String definition )
    {
        def = definition.trim();

        Matcher m = PATTERN.matcher( def );
        if ( !m.matches() )
        {
            throw new IllegalArgumentException( "bad syntax: " + def );
        }

        reference = m.group( 1 );
        if ( reference != null )
        {
            return;
        }

        coords = m.group( 2 );
        range = m.group( 3 );
        premanagedVersion = m.group( 4 );

        String props = m.group( 5 );
        if ( props != null )
        {
            properties = new LinkedHashMap<String, String>();
            for ( String prop : props.split( "\\s*,\\s*" ) )
            {
                int sep = prop.indexOf( ':' );
                String key = prop.substring( 0, sep );
                String val = prop.substring( sep + 1 );
                properties.put( key, val );
            }
        }

        scope = m.group( 6 );
        premanagedScope = m.group( 7 );
        if ( m.group( 8 ) != null )
        {
            optional = !m.group( 8 ).startsWith( "!" );
        }

        String relocs = m.group( 9 );
        if ( relocs != null )
        {
            relocations = new ArrayList<String>();
            Collections.addAll( relocations, relocs.split( "\\s*,\\s*" ) );
        }

        id = m.group( 10 );
    }

    @Override
    public String toString()
    {
        return def;
    }

}
