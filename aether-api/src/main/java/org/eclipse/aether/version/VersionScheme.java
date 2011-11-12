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
package org.eclipse.aether.version;

/**
 * A version scheme that handles interpretation of version strings to facilitate their comparison.
 */
public interface VersionScheme
{

    /**
     * Parses the specified version string, for example "1.0".
     * 
     * @param version The version string to parse, must not be {@code null}.
     * @return The parsed version, never {@code null}.
     * @throws InvalidVersionSpecificationException If the string violates the syntax rules of this scheme.
     */
    Version parseVersion( String version )
        throws InvalidVersionSpecificationException;

    /**
     * Parses the specified version range specification, for example "[1.0,2.0)".
     * 
     * @param range The range specification to parse, must not be {@code null}.
     * @return The parsed version range, never {@code null}.
     * @throws InvalidVersionSpecificationException If the range specification violates the syntax rules of this scheme.
     */
    VersionRange parseVersionRange( String range )
        throws InvalidVersionSpecificationException;

    /**
     * Parses the specified version constraint specification, for example "1.0" or "[1.0,2.0),(2.0,)".
     * 
     * @param constraint The constraint specification to parse, must not be {@code null}.
     * @return The parsed version constraint, never {@code null}.
     * @throws InvalidVersionSpecificationException If the constraint specification violates the syntax rules of this
     *             scheme.
     */
    VersionConstraint parseVersionConstraint( final String constraint )
        throws InvalidVersionSpecificationException;

}
