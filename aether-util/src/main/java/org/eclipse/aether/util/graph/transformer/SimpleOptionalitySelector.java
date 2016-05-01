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
package org.eclipse.aether.util.graph.transformer;

import java.util.Collection;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictContext;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictItem;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.OptionalitySelector;

/**
 * An optionality selector for use with {@link ConflictResolver}. In general, this selector only marks a dependency as
 * optional if all its occurrences are optional. If however a direct dependency is involved, its optional flag is
 * selected.
 */
public final class SimpleOptionalitySelector
    extends OptionalitySelector
{

    /**
     * Creates a new instance of this optionality selector.
     */
    public SimpleOptionalitySelector()
    {
    }

    @Override
    public void selectOptionality( ConflictContext context )
        throws RepositoryException
    {
        boolean optional = chooseEffectiveOptionality( context.getItems() );
        context.setOptional( optional );
    }

    private boolean chooseEffectiveOptionality( Collection<ConflictItem> items )
    {
        boolean optional = true;
        for ( ConflictItem item : items )
        {
            if ( item.getDepth() <= 1 )
            {
                return item.getDependency().isOptional();
            }
            if ( ( item.getOptionalities() & ConflictItem.OPTIONAL_FALSE ) != 0 )
            {
                optional = false;
            }
        }
        return optional;
    }

}
