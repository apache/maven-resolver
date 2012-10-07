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
package org.eclipse.aether.version;

/**
 * A range of versions.
 */
public interface VersionRange
{

    /**
     * Determines whether the specified version is contained within this range.
     * 
     * @param version The version to test, must not be {@code null}.
     * @return {@code true} if this range contains the specified version, {@code false} otherwise.
     */
    boolean containsVersion( Version version );

    /**
     * Gets a lower bound (if any) for this range. If existent, this range does not contain any version smaller than its
     * lower bound. Note that complex version ranges might exclude some versions even within their bounds.
     * 
     * @return A lower bound for this range or {@code null} is there is none.
     */
    Bound getLowerBound();

    /**
     * Gets an upper bound (if any) for this range. If existent, this range does not contain any version greater than
     * its upper bound. Note that complex version ranges might exclude some versions even within their bounds.
     * 
     * @return An upper bound for this range or {@code null} is there is none.
     */
    Bound getUpperBound();

    /**
     * A bound of a version range.
     */
    static final class Bound
    {

        private final Version version;

        private final boolean inclusive;

        /**
         * Creates a new bound with the specified properties.
         * 
         * @param version The bounding version, must not be {@code null}.
         * @param inclusive A flag whether the specified version is included in the range or not.
         */
        public Bound( Version version, boolean inclusive )
        {
            if ( version == null )
            {
                throw new IllegalArgumentException( "version missing" );
            }
            this.version = version;
            this.inclusive = inclusive;
        }

        /**
         * Gets the bounding version.
         * 
         * @return The bounding version, never {@code null}.
         */
        public Version getVersion()
        {
            return version;
        }

        /**
         * Indicates whether the bounding version is included in the range or not.
         * 
         * @return {@code true} if the bounding version is included in the range, {@code false} if not.
         */
        public boolean isInclusive()
        {
            return inclusive;
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( obj == this )
            {
                return true;
            }
            else if ( obj == null || !getClass().equals( obj.getClass() ) )
            {
                return false;
            }

            Bound that = (Bound) obj;
            return inclusive == that.inclusive && version.equals( that.version );
        }

        @Override
        public int hashCode()
        {
            int hash = 17;
            hash = hash * 31 + version.hashCode();
            hash = hash * 31 + ( inclusive ? 1 : 0 );
            return hash;
        }

        @Override
        public String toString()
        {
            return String.valueOf( version );
        }

    }

}
