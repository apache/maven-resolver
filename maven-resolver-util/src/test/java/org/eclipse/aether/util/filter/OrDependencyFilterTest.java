/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.util.filter;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.NodeBuilder;
import org.junit.Test;

import static org.junit.Assert.*;

public class OrDependencyFilterTest extends AbstractDependencyFilterTest {

    @Test
    public void acceptTest() {
        NodeBuilder builder = new NodeBuilder();
        builder.artifactId("test");
        List<DependencyNode> parents = new LinkedList<>();
        // Empty OR
        assertFalse(new OrDependencyFilter().accept(builder.build(), parents));

        // Basic Boolean Input
        assertTrue(new OrDependencyFilter(getAcceptFilter()).accept(builder.build(), parents));
        assertFalse(new OrDependencyFilter(getDenyFilter()).accept(builder.build(), parents));

        assertFalse(new OrDependencyFilter(getDenyFilter(), getDenyFilter()).accept(builder.build(), parents));
        assertTrue(new OrDependencyFilter(getDenyFilter(), getAcceptFilter()).accept(builder.build(), parents));
        assertTrue(new OrDependencyFilter(getAcceptFilter(), getDenyFilter()).accept(builder.build(), parents));
        assertTrue(new OrDependencyFilter(getAcceptFilter(), getAcceptFilter()).accept(builder.build(), parents));

        assertFalse(new OrDependencyFilter(getDenyFilter(), getDenyFilter(), getDenyFilter())
                .accept(builder.build(), parents));
        assertTrue(new OrDependencyFilter(getAcceptFilter(), getDenyFilter(), getDenyFilter())
                .accept(builder.build(), parents));
        assertTrue(new OrDependencyFilter(getAcceptFilter(), getAcceptFilter(), getDenyFilter())
                .accept(builder.build(), parents));
        assertTrue(new OrDependencyFilter(getAcceptFilter(), getAcceptFilter(), getAcceptFilter())
                .accept(builder.build(), parents));

        // User another constructor
        Collection<DependencyFilter> filters = new LinkedList<>();
        filters.add(getDenyFilter());
        filters.add(getAcceptFilter());
        assertTrue(new OrDependencyFilter(filters).accept(builder.build(), parents));

        filters = new LinkedList<>();
        filters.add(getDenyFilter());
        filters.add(getDenyFilter());
        assertFalse(new OrDependencyFilter(filters).accept(builder.build(), parents));

        // newInstance
        assertTrue(AndDependencyFilter.newInstance(getAcceptFilter(), getAcceptFilter())
                .accept(builder.build(), parents));
        assertFalse(AndDependencyFilter.newInstance(getAcceptFilter(), getDenyFilter())
                .accept(builder.build(), parents));
        assertTrue(AndDependencyFilter.newInstance(getAcceptFilter(), null).accept(builder.build(), parents));
        assertFalse(AndDependencyFilter.newInstance(getDenyFilter(), null).accept(builder.build(), parents));
        assertNull(AndDependencyFilter.newInstance(null, null));
    }
}
