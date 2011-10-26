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
package org.eclipse.aether.connector.async;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.connector.async.AsyncRepositoryConnectorFactory;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.test.impl.TestFileProcessor;
import org.eclipse.aether.test.impl.TestRepositorySystemSession;
import org.eclipse.aether.test.util.TestFileUtils;
import org.eclipse.aether.test.util.impl.StubArtifact;
import org.eclipse.aether.test.util.impl.StubMetadata;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.tests.http.runner.junit.DefaultSuiteConfiguration;
import org.sonatype.tests.http.server.api.ServerProvider;
import org.sonatype.tests.http.server.jetty.behaviour.Expect;
import org.sonatype.tests.http.server.jetty.behaviour.Provide;

/**
 */
public class AsyncConnectorSuiteConfiguration
    extends DefaultSuiteConfiguration
{

    protected Logger logger = LoggerFactory.getLogger( this.getClass() );

    private AsyncRepositoryConnectorFactory factory;

    private TestRepositorySystemSession session;

    private RemoteRepository repository;

    private Artifact artifact;

    private Metadata metadata;

    protected Expect expect;

    protected Provide provide = new Provide();

    protected Generate generate;

    private RepositoryConnector connector;

    @Override
    @Before
    public void before()
        throws Exception
    {
        super.before();

        this.factory = new AsyncRepositoryConnectorFactory( new TestFileProcessor() );
        this.session = new TestRepositorySystemSession();
        this.repository = new RemoteRepository( "async-test-repo", "default", url( "repo" ) );
        
        this.artifact = new StubArtifact( "gid", "aid", "classifier", "extension", "version", null );
        this.metadata =
            new StubMetadata( "gid", "aid", "version", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT, null );

        connector = null;

    }

    @Override
    @After
    public void after()
        throws Exception
    {
        super.after();
        connector().close();
        TestFileUtils.deleteTempFiles();
    }

    protected RepositoryConnectorFactory factory()
    {
        return factory;
    }

    protected RepositoryConnector connector()
        throws NoRepositoryConnectorException
    {
        if ( connector == null )
        {
            connector = factory().newInstance( session(), repository() );
        }
        return connector;
    }

    /**
     * @return
     */
    protected TestRepositorySystemSession session()
    {
        return session;
    }

    /**
     * @return
     */
    protected RemoteRepository repository()
    {
        return repository;
    }

    protected Artifact artifact()
    {
        return artifact;
    }

    protected Artifact artifact( String content )
        throws IOException
    {
        return artifact().setFile( TestFileUtils.createTempFile( content ) );
    }

    protected Metadata metadata()
    {
        return metadata;
    }

    protected Metadata metadata( String content )
        throws IOException
    {
        return metadata().setFile( TestFileUtils.createTempFile( content ) );
    }

    protected String md5( String string )
        throws NoSuchAlgorithmException, UnsupportedEncodingException
    {
        String algo = "MD5";
        return digest( string, algo );
    }

    private String digest( String string, String algo )
        throws NoSuchAlgorithmException, UnsupportedEncodingException
    {
        MessageDigest digest = MessageDigest.getInstance( algo );
        byte[] bytes = digest.digest( string.getBytes( "UTF-8" ) );
        StringBuilder buffer = new StringBuilder( 64 );
    
        for ( int i = 0; i < bytes.length; i++ )
        {
            int b = bytes[i] & 0xFF;
            if ( b < 0x10 )
            {
                buffer.append( '0' );
            }
            buffer.append( Integer.toHexString( b ) );
        }
        return buffer.toString();
    }

    protected String sha1( String string )
        throws NoSuchAlgorithmException, UnsupportedEncodingException
    {
        return digest( string, "SHA-1" );
    }

    protected void assertExpectations()
    {
        expect.assertExpectations();
    }

    protected Expect addExpectation( String path, String content )
        throws Exception
    {
        byte[] bytes = content.getBytes( "UTF-8" );
        return addExpectation( path, bytes );
    }

    private Expect addExpectation( String path, byte[] content )
    {
        expect.addExpectation( path, content );
        return expect;
    }

    protected void addDelivery( String path, String content )
        throws Exception
    {
        addDelivery( path, content.getBytes( "UTF-8" ) );
    }

    @Override
    public void configureProvider( ServerProvider provider )
    {
        super.configureProvider( provider );
        expect = new Expect();
        provide = new Provide();
        generate = new Generate();
        provider.addBehaviour( "/repo", generate, expect, provide );
    }

    protected void addDelivery( String path, byte[] content )
    {
        provide.addPath( path, content );
    }

}
