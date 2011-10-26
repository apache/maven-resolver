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
package org.eclipse.aether.util.listener;

import java.io.File;

import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.transfer.TransferResource;

/**
 * A simple transfer resource.
 */
public final class DefaultTransferResource
    implements TransferResource
{

    private final String repositoryUrl;

    private final String resourceName;

    private final File file;

    private final long startTime;

    private final RequestTrace trace;

    private long contentLength = -1;

    /**
     * Creates a new transfer resource with the specified properties.
     * 
     * @param repositoryUrl The base URL of the repository, may be {@code null} or empty if unknown. If not empty, a
     *            trailing slash will automatically be added if missing.
     * @param resourceName The relative path to the resource within the repository, may be {@code null}. A leading slash
     *            (if any) will be automatically removed.
     * @param file The source/target file involved in the transfer, may be {@code null}.
     * @param trace The trace information, may be {@code null}.
     */
    public DefaultTransferResource( String repositoryUrl, String resourceName, File file, RequestTrace trace )
    {
        if ( repositoryUrl == null || repositoryUrl.length() <= 0 )
        {
            this.repositoryUrl = "";
        }
        else if ( repositoryUrl.endsWith( "/" ) )
        {
            this.repositoryUrl = repositoryUrl;
        }
        else
        {
            this.repositoryUrl = repositoryUrl + '/';
        }

        if ( resourceName == null || resourceName.length() <= 0 )
        {
            this.resourceName = "";
        }
        else if ( resourceName.startsWith( "/" ) )
        {
            this.resourceName = resourceName.substring( 1 );
        }
        else
        {
            this.resourceName = resourceName;
        }

        this.file = file;

        this.trace = trace;

        startTime = System.currentTimeMillis();
    }

    public String getRepositoryUrl()
    {
        return repositoryUrl;
    }

    public String getResourceName()
    {
        return resourceName;
    }

    public File getFile()
    {
        return file;
    }

    public long getContentLength()
    {
        return contentLength;
    }

    /**
     * Sets the size of the resource in bytes.
     * 
     * @param contentLength The size of the resource in bytes or a negative value if unknown.
     * @return This resource for chaining, never {@code null}.
     */
    public DefaultTransferResource setContentLength( long contentLength )
    {
        this.contentLength = contentLength;
        return this;
    }

    public long getTransferStartTime()
    {
        return startTime;
    }

    public RequestTrace getTrace()
    {
        return trace;
    }

    @Override
    public String toString()
    {
        return getRepositoryUrl() + getResourceName() + " <> " + getFile();
    }

}
