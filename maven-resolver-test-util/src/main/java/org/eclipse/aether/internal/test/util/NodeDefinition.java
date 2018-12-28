package org.eclipse.aether.internal.test.util;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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

    NodeDefinition( String definition )
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
            properties = new LinkedHashMap<>();
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
        optional = ( m.group( 8 ) != null ) ? !m.group( 8 ).startsWith( "!" ) : Boolean.FALSE;

        String relocs = m.group( 9 );
        if ( relocs != null )
        {
            relocations = new ArrayList<>();
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
