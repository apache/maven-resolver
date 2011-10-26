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
package org.eclipse.aether.transfer;


/**
 * A skeleton implementation for custom transfer listeners. The callback methods in this class do nothing.
 */
public abstract class AbstractTransferListener
    implements TransferListener
{

    public void transferInitiated( TransferEvent event )
        throws TransferCancelledException
    {
    }

    public void transferStarted( TransferEvent event )
        throws TransferCancelledException
    {
    }

    public void transferProgressed( TransferEvent event )
        throws TransferCancelledException
    {
    }

    public void transferCorrupted( TransferEvent event )
        throws TransferCancelledException
    {
    }

    public void transferSucceeded( TransferEvent event )
    {
    }

    public void transferFailed( TransferEvent event )
    {
    }

}
