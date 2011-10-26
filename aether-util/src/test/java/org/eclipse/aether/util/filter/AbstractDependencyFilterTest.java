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
package org.eclipse.aether.util.filter;

import java.util.List;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;

public abstract class AbstractDependencyFilterTest
{

    protected DependencyFilter getAcceptFilter()
    {
        return new DependencyFilter()
        {

            public boolean accept( DependencyNode node, List<DependencyNode> parents )
            {
                return true;
            }

        };
    }

    protected DependencyFilter getDenyFilter()
    {
        return new DependencyFilter()
        {

            public boolean accept( DependencyNode node, List<DependencyNode> parents )
            {
                return false;
            }
        };
    }


}
