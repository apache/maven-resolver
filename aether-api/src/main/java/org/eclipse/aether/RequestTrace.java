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
package org.eclipse.aether;

/**
 * A trace of nested requests that are performed by the repository system. This trace information can be used to
 * correlate repository events with higher level operations in the application code that eventually caused the events. A
 * single trace can carry an arbitrary object as data which is meant to describe a request/operation that is currently
 * executed. For call hierarchies within the repository system itself, this data will usually be the {@code *Request}
 * object that is currently processed. When invoking methods on the repository system, client code may provide a request
 * trace that has been prepopulated with whatever data is useful for the application to indicate its state for later
 * evaluation when processing the repository events.
 * 
 * @see RepositoryEvent#getTrace()
 */
public interface RequestTrace
{

    /**
     * Gets the data associated with this trace.
     * 
     * @return The data associated with this trace or {@code null}.
     */
    Object getData();

    /**
     * Gets the parent of this trace.
     * 
     * @return The parent of this trace or {@code null} if this is the root of the trace stack.
     */
    RequestTrace getParent();

    /**
     * Creates a new child of this trace.
     * 
     * @param data The data to associate with the child, may be {@code null}.
     * @return The child trace, never {@code null}.
     */
    RequestTrace newChild( Object data );

}
