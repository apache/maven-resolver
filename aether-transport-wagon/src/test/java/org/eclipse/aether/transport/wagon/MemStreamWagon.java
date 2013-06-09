/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.transport.wagon;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.InputData;
import org.apache.maven.wagon.OutputData;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.StreamWagon;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.resource.Resource;

/**
 */
public class MemStreamWagon
    extends StreamWagon
    implements Configurable
{

    private Map<String, String> fs;

    private Properties headers;

    private Object config;

    public void setConfiguration( Object config )
    {
        this.config = config;
    }

    public Object getConfiguration()
    {
        return config;
    }

    public void setHttpHeaders( Properties httpHeaders )
    {
        headers = httpHeaders;
    }

    @Override
    protected void openConnectionInternal()
        throws ConnectionException, AuthenticationException
    {
        fs =
            MemWagonUtils.openConnection( this, getAuthenticationInfo(),
                                          getProxyInfo( "mem", getRepository().getHost() ), headers );
    }

    @Override
    public void closeConnection()
        throws ConnectionException
    {
        fs = null;
    }

    private String getData( String resource )
    {
        return fs.get( URI.create( resource ).getSchemeSpecificPart() );
    }

    @Override
    public boolean resourceExists( String resourceName )
        throws TransferFailedException, AuthorizationException
    {
        String data = getData( resourceName );
        return data != null;
    }

    @Override
    public void fillInputData( InputData inputData )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        String data = getData( inputData.getResource().getName() );
        if ( data == null )
        {
            throw new ResourceDoesNotExistException( "Missing resource: " + inputData.getResource().getName() );
        }
        byte[] bytes;
        try
        {
            bytes = data.getBytes( "UTF-8" );
        }
        catch ( UnsupportedEncodingException e )
        {
            throw new TransferFailedException( e.getMessage(), e );
        }
        inputData.getResource().setContentLength( bytes.length );
        inputData.setInputStream( new ByteArrayInputStream( bytes ) );
    }

    @Override
    public void fillOutputData( OutputData outputData )
        throws TransferFailedException
    {
        outputData.setOutputStream( new ByteArrayOutputStream() );
    }

    @Override
    protected void finishPutTransfer( Resource resource, InputStream input, OutputStream output )
        throws TransferFailedException, AuthorizationException, ResourceDoesNotExistException
    {
        String data;
        try
        {
            data = ( (ByteArrayOutputStream) output ).toString( "UTF-8" );
        }
        catch ( UnsupportedEncodingException e )
        {
            throw new TransferFailedException( e.getMessage(), e );
        }
        fs.put( URI.create( resource.getName() ).getSchemeSpecificPart(), data );
    }

}
