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
package org.eclipse.aether.util;

/**
 * A utility class to ease string processing.
 */
public final class StringUtils
{

    private StringUtils()
    {
        // hide constructor
    }

    /**
     * Checks whether a string is {@code null} or of zero length.
     * 
     * @param string The string to check, may be {@code null}.
     * @return {@code true} if the string is {@code null} or of zero length, {@code false} otherwise.
     */
    public static boolean isEmpty( String string )
    {
        return string == null || string.length() <= 0;
    }

}
