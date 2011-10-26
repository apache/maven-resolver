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
package org.eclipse.aether.test.util;

class ArtifactDefinition
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

    public ArtifactDefinition( String def )
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
        if ( split.length > 5 && "optional".equalsIgnoreCase( split[5] ) )
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

    public String getExtension()
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