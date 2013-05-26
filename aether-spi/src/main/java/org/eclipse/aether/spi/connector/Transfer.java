/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.spi.connector;

import org.eclipse.aether.RequestTrace;

/**
 * An artifact/metadata transfer.
 * 
 * @noextend This class is not intended to be extended by clients.
 */
public abstract class Transfer
{

    /**
     * The state of a transfer.
     */
    public enum State
    {
        /**
         * Transfer has not started yet.
         */
        NEW,

        /**
         * Transfer is in progress.
         */
        ACTIVE,

        /**
         * Transfer is over, either successfully or not.
         */
        DONE
    }

    private State state = State.NEW;

    private RequestTrace trace;

    Transfer()
    {
        // hide
    }

    /**
     * Gets the state of this transfer.
     * 
     * @return The state of this transfer, never {@code null}.
     */
    public State getState()
    {
        return state;
    }

    /**
     * Sets the state of this transfer.
     * 
     * @param state The new state, must not be {@code null}.
     * @return This transfer for chaining, never {@code null}.
     */
    public Transfer setState( State state )
    {
        if ( state == null )
        {
            throw new IllegalArgumentException( "no transfer state specified" );
        }
        this.state = state;
        return this;
    }

    /**
     * Gets the trace information that describes the higher level request/operation in which this transfer is issued.
     * 
     * @return The trace information about the higher level operation or {@code null} if none.
     */
    public RequestTrace getTrace()
    {
        return trace;
    }

    /**
     * Sets the trace information that describes the higher level request/operation in which this transfer is issued.
     * 
     * @param trace The trace information about the higher level operation, may be {@code null}.
     * @return This transfer for chaining, never {@code null}.
     */
    public Transfer setTrace( RequestTrace trace )
    {
        this.trace = trace;
        return this;
    }

}
