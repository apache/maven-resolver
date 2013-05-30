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
package org.eclipse.aether.transport.file;

/**
 * URL handling for file URLs. Based on org.apache.maven.wagon.PathUtils.
 */
final class PathUtils
{

    private PathUtils()
    {
    }

    /**
     * Return the protocol name. <br/>
     * E.g: for input <code>http://www.codehause.org</code> this method will return <code>http</code>
     * 
     * @param url the url
     * @return the host name
     */
    public static String protocol( final String url )
    {
        final int pos = url.indexOf( ":" );

        if ( pos == -1 )
        {
            return "";
        }
        return url.substring( 0, pos ).trim();
    }

    /**
     * Derive the path portion of the given URL.
     * 
     * @param url the file-repository URL
     * @return the basedir of the repository
     */
    public static String basedir( String url )
    {
        String protocol = PathUtils.protocol( url );

        String retValue = null;

        if ( protocol.length() > 0 )
        {
            retValue = url.substring( protocol.length() + 1 );
        }
        else
        {
            retValue = url;
        }
        retValue = decode( retValue );
        // special case: if omitted // on protocol, keep path as is
        if ( retValue.startsWith( "//" ) )
        {
            retValue = retValue.substring( 2 );

            if ( retValue.length() >= 2 && ( retValue.charAt( 1 ) == '|' || retValue.charAt( 1 ) == ':' ) )
            {
                // special case: if there is a windows drive letter, then keep the original return value
                retValue = retValue.charAt( 0 ) + ":" + retValue.substring( 2 );
            }
            else
            {
                // Now we expect the host
                int index = retValue.indexOf( "/" );
                if ( index >= 0 )
                {
                    retValue = retValue.substring( index + 1 );
                }

                // special case: if there is a windows drive letter, then keep the original return value
                if ( retValue.length() >= 2 && ( retValue.charAt( 1 ) == '|' || retValue.charAt( 1 ) == ':' ) )
                {
                    retValue = retValue.charAt( 0 ) + ":" + retValue.substring( 2 );
                }
                else if ( index >= 0 )
                {
                    // leading / was previously stripped
                    retValue = "/" + retValue;
                }
            }
        }

        // special case: if there is a windows drive letter using |, switch to :
        if ( retValue.length() >= 2 && retValue.charAt( 1 ) == '|' )
        {
            retValue = retValue.charAt( 0 ) + ":" + retValue.substring( 2 );
        }

        return retValue.trim();
    }

    /**
     * Decodes the specified (portion of a) URL. <strong>Note:</strong> This decoder assumes that ISO-8859-1 is used to
     * convert URL-encoded octets to characters.
     * 
     * @param url The URL to decode, may be <code>null</code>.
     * @return The decoded URL or <code>null</code> if the input was <code>null</code>.
     */
    static String decode( String url )
    {
        String decoded = url;
        if ( url != null )
        {
            int pos = -1;
            while ( ( pos = decoded.indexOf( '%', pos + 1 ) ) >= 0 )
            {
                if ( pos + 2 < decoded.length() )
                {
                    String hexStr = decoded.substring( pos + 1, pos + 3 );
                    char ch = (char) Integer.parseInt( hexStr, 16 );
                    decoded = decoded.substring( 0, pos ) + ch + decoded.substring( pos + 3 );
                }
            }
        }
        return decoded;
    }

}
