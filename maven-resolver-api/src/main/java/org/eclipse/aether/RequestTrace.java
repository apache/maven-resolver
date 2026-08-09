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
public class RequestTrace {

    private final RequestTrace parent;

    private final Object data;

    /**
     * Creates a child of the specified request trace. This method is basically a convenience that will invoke
     * {@link RequestTrace#newChild(Object) parent.newChild()} when the specified parent trace is not {@code null} or
     * otherwise instantiante a new root trace.
     *
     * @param parent The parent request trace, may be {@code null}.
     * @param data The data to associate with the child trace, may be {@code null}.
     * @return The child trace, never {@code null}.
     */
    public static RequestTrace newChild(RequestTrace parent, Object data) {
        if (parent == null) {
            return new RequestTrace(data);
        }
        return parent.newChild(data);
    }

    /**
     * Creates a new root trace with the specified data.
     *
     * @param data The data to associate with the trace, may be {@code null}.
     */
    public RequestTrace(Object data) {
        this(null, data);
    }

    /**
     * Creates a new trace with the specified data and parent
     *
     * @param parent The parent trace, may be {@code null} for a root trace.
     * @param data The data to associate with the trace, may be {@code null}.
     */
    protected RequestTrace(RequestTrace parent, Object data) {
        this.parent = parent;
        this.data = data;
    }

    /**
     * Gets the data associated with this trace.
     *
     * @return The data associated with this trace or {@code null} if none.
     */
    public final Object getData() {
        return data;
    }

    /**
     * Gets the parent of this trace.
     *
     * @return The parent of this trace or {@code null} if this is the root of the trace stack.
     */
    public final RequestTrace getParent() {
        return parent;
    }

    /**
     * Creates a new child of this trace.
     *
     * @param data The data to associate with the child, may be {@code null}.
     * @return The child trace, never {@code null}.
     */
    public RequestTrace newChild(Object data) {
        return new RequestTrace(this, data);
    }

    @Override
    public String toString() {
        return String.valueOf(getData());
    }
}
