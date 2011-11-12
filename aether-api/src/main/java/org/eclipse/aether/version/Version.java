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
 * A parsed artifact version.
 */
public interface Version
    extends Comparable<Version>
{

    /**
     * Gets the original string representation of the version.
     * 
     * @return The string representation of the version.
     */
    String toString();

}
