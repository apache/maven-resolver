/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
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
 * A constraint on versions for a dependency. A constraint can either consist of a version range (e.g. "[1, ]") or a
 * single version (e.g. "1.1"). In the first case, the constraint expresses a hard requirement on a version matching the
 * range. In the second case, the constraint expresses a soft requirement on a specific version (i.e. a recommendation).
 */
public interface VersionConstraint
{

    /**
     * Gets the version range of this constraint.
     * 
     * @return The version range or {@code null} if none.
     */
    VersionRange getRange();

    /**
     * Gets the version recommended by this constraint.
     * 
     * @return The recommended version or {@code null} if none.
     */
    Version getVersion();

    /**
     * Determines whether the specified version satisfies this constraint. In more detail, a version satisfies this
     * constraint if it matches its version range or if this constraint has no version range and the specified version
     * equals the version recommended by the constraint.
     * 
     * @param version The version to test, must not be {@code null}.
     * @return {@code true} if the specified version satisfies this constraint, {@code false} otherwise.
     */
    boolean containsVersion( Version version );

}
