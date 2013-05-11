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
package org.eclipse.aether.internal.impl;

import static org.junit.Assert.*;

import java.io.File;
import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.UpdateCheck;
import org.eclipse.aether.internal.impl.DefaultUpdateCheckManager;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.util.repository.SimpleResolutionErrorPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class DefaultUpdateCheckManagerTest
{

    private static final int HOUR = 60 * 60 * 1000;

    private DefaultUpdateCheckManager manager;

    private DefaultRepositorySystemSession session;

    private Metadata metadata;

    private RemoteRepository repository;

    private Artifact artifact;

    @Before
    public void setup()
        throws Exception
    {
        File dir = TestFileUtils.createTempFile( "" );
        TestFileUtils.delete( dir );

        File metadataFile = new File( dir, "metadata.txt" );
        TestFileUtils.write( "metadata", metadataFile );
        File artifactFile = new File( dir, "artifact.txt" );
        TestFileUtils.write( "artifact", artifactFile );

        session = TestUtils.newSession();
        repository = new RemoteRepository.Builder( "id", "default", TestFileUtils.createTempDir().toURI().toURL().toString() ).build();
        manager = new DefaultUpdateCheckManager().setUpdatePolicyAnalyzer( new DefaultUpdatePolicyAnalyzer() );
        metadata =
            new DefaultMetadata( "gid", "aid", "ver", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT,
                                 metadataFile );
        artifact = new DefaultArtifact( "gid", "aid", "", "ext", "ver" ).setFile( artifactFile );
    }

    @After
    public void teardown()
        throws Exception
    {
        new File( metadata.getFile().getParent(), "resolver-status.properties" ).delete();
        new File( artifact.getFile().getPath() + ".lastUpdated" ).delete();
        metadata.getFile().delete();
        artifact.getFile().delete();
        TestFileUtils.delete( new File( new URI( repository.getUrl() ) ) );
    }

    static void resetSessionData( RepositorySystemSession session )
    {
        session.getData().set( "updateCheckManager.checks", null );
    }

    private UpdateCheck<Metadata, MetadataTransferException> newMetadataCheck()
    {
        UpdateCheck<Metadata, MetadataTransferException> check = new UpdateCheck<Metadata, MetadataTransferException>();
        check.setItem( metadata );
        check.setFile( metadata.getFile() );
        check.setRepository( repository );
        check.setAuthoritativeRepository( repository );
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":10" );
        return check;
    }

    private UpdateCheck<Artifact, ArtifactTransferException> newArtifactCheck()
    {
        UpdateCheck<Artifact, ArtifactTransferException> check = new UpdateCheck<Artifact, ArtifactTransferException>();
        check.setItem( artifact );
        check.setFile( artifact.getFile() );
        check.setRepository( repository );
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":10" );
        return check;
    }

    @Test( expected = Exception.class )
    public void testCheckMetadataFailOnNoFile()
        throws Exception
    {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setItem( metadata.setFile( null ) );
        check.setFile( null );

        manager.checkMetadata( session, check );
    }

    @Test
    public void testCheckMetadataUpdatePolicyRequired()
        throws Exception
    {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();

        Calendar cal = Calendar.getInstance();
        cal.add( Calendar.DATE, -1 );
        check.setLocalLastUpdated( cal.getTimeInMillis() );

        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_ALWAYS );
        manager.checkMetadata( session, check );
        assertNull( check.getException() );
        assertTrue( check.isRequired() );

        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_DAILY );
        manager.checkMetadata( session, check );
        assertNull( check.getException() );
        assertTrue( check.isRequired() );

        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":60" );
        manager.checkMetadata( session, check );
        assertNull( check.getException() );
        assertTrue( check.isRequired() );
    }

    @Test
    public void testCheckMetadataUpdatePolicyNotRequired()
        throws Exception
    {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();

        check.setLocalLastUpdated( System.currentTimeMillis() );

        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_NEVER );
        manager.checkMetadata( session, check );
        assertFalse( check.isRequired() );

        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_DAILY );
        manager.checkMetadata( session, check );
        assertFalse( check.isRequired() );

        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":61" );
        manager.checkMetadata( session, check );
        assertFalse( check.isRequired() );

        check.setPolicy( "no particular policy" );
        manager.checkMetadata( session, check );
        assertFalse( check.isRequired() );
    }

    @Test
    public void testCheckMetadata()
        throws Exception
    {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_DAILY );

        // existing file, never checked before
        manager.checkMetadata( session, check );
        assertEquals( true, check.isRequired() );

        // just checked
        manager.touchMetadata( session, check );
        resetSessionData( session );

        check = newMetadataCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":60" );

        manager.checkMetadata( session, check );
        assertEquals( false, check.isRequired() );

        // no local file
        check.getFile().delete();
        manager.checkMetadata( session, check );
        assertEquals( true, check.isRequired() );
        // (! file.exists && ! repoKey) -> no timestamp
    }

    @Test
    public void testCheckMetadataNoLocalFile()
        throws Exception
    {
        metadata.getFile().delete();

        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();

        long lastUpdate = new Date().getTime() - HOUR;
        check.setLocalLastUpdated( lastUpdate );

        // ! file.exists && updateRequired -> check in remote repo
        check.setLocalLastUpdated( lastUpdate );
        manager.checkMetadata( session, check );
        assertEquals( true, check.isRequired() );
    }

    @Test
    public void testCheckMetadataNotFoundInRepoCachingEnabled()
        throws Exception
    {
        metadata.getFile().delete();
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( true, false ) );

        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();

        check.setException( new MetadataNotFoundException( metadata, repository, "" ) );
        manager.touchMetadata( session, check );
        resetSessionData( session );

        // ! file.exists && ! updateRequired -> artifact not found in remote repo
        check = newMetadataCheck().setPolicy( RepositoryPolicy.UPDATE_POLICY_DAILY );
        manager.checkMetadata( session, check );
        assertEquals( false, check.isRequired() );
        assertNotNull( check.getException() );
    }

    @Test
    public void testCheckMetadataNotFoundInRepoCachingDisabled()
        throws Exception
    {
        metadata.getFile().delete();
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( false, false ) );

        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();

        check.setException( new MetadataNotFoundException( metadata, repository, "" ) );
        manager.touchMetadata( session, check );
        resetSessionData( session );

        // ! file.exists && updateRequired -> check in remote repo
        check = newMetadataCheck().setPolicy( RepositoryPolicy.UPDATE_POLICY_DAILY );
        manager.checkMetadata( session, check );
        assertEquals( true, check.isRequired() );
        assertNull( check.getException() );
    }

    @Test
    public void testCheckMetadataErrorFromRepo()
        throws Exception
    {
        metadata.getFile().delete();

        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_DAILY );

        check.setException( new MetadataTransferException( metadata, repository, "some error" ) );
        manager.touchMetadata( session, check );
        resetSessionData( session );

        // ! file.exists && ! updateRequired && previousError -> depends on transfer error caching
        check = newMetadataCheck();
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( false, true ) );
        manager.checkMetadata( session, check );
        assertEquals( false, check.isRequired() );
        assertTrue( check.getException() instanceof MetadataTransferException );
        assertTrue( String.valueOf( check.getException() ), check.getException().getMessage().contains( "some error" ) );
    }

    @Test
    public void testCheckMetadataErrorFromRepoNoCaching()
        throws Exception
    {
        metadata.getFile().delete();

        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_DAILY );

        check.setException( new MetadataTransferException( metadata, repository, "some error" ) );
        manager.touchMetadata( session, check );
        resetSessionData( session );

        // ! file.exists && ! updateRequired && previousError -> depends on transfer error caching
        check = newMetadataCheck();
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( false, false ) );
        manager.checkMetadata( session, check );
        assertEquals( true, check.isRequired() );
        assertNull( check.getException() );
    }

    @Test
    public void testCheckMetadataAtMostOnceDuringSessionEvenIfUpdatePolicyAlways()
        throws Exception
    {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_ALWAYS );

        // first check
        manager.checkMetadata( session, check );
        assertEquals( true, check.isRequired() );

        manager.touchMetadata( session, check );

        // second check in same session
        manager.checkMetadata( session, check );
        assertEquals( false, check.isRequired() );
    }

    @Test
    public void testCheckMetadataAtMostOnceDuringSessionEvenIfUpdatePolicyAlways_InvalidFile()
        throws Exception
    {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_ALWAYS );
        check.setFileValid( false );

        // first check
        manager.checkMetadata( session, check );
        assertEquals( true, check.isRequired() );

        manager.touchMetadata( session, check );

        // second check in same session
        manager.checkMetadata( session, check );
        assertEquals( false, check.isRequired() );
    }

    @Test
    public void testCheckMetadataWhenLocallyMissingEvenIfUpdatePolicyIsNever()
        throws Exception
    {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_NEVER );
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( true, false ) );

        check.getFile().delete();
        assertEquals( check.getFile().getAbsolutePath(), false, check.getFile().exists() );

        manager.checkMetadata( session, check );
        assertEquals( true, check.isRequired() );
    }

    @Test
    public void testCheckMetadataWhenLocallyPresentButInvalidEvenIfUpdatePolicyIsNever()
        throws Exception
    {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_NEVER );
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( true, false ) );

        manager.touchMetadata( session, check );
        resetSessionData( session );

        check.setFileValid( false );

        manager.checkMetadata( session, check );
        assertEquals( true, check.isRequired() );
    }

    @Test
    public void testCheckMetadataWhenLocallyDeletedEvenIfTimestampUpToDate()
        throws Exception
    {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( true, false ) );

        manager.touchMetadata( session, check );
        resetSessionData( session );

        check.getFile().delete();
        assertEquals( check.getFile().getAbsolutePath(), false, check.getFile().exists() );

        manager.checkMetadata( session, check );
        assertEquals( true, check.isRequired() );
    }

    @Test
    public void testCheckMetadataNotWhenUpdatePolicyIsNeverAndTimestampIsUnavailable()
        throws Exception
    {
        UpdateCheck<Metadata, MetadataTransferException> check = newMetadataCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_NEVER );
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( true, false ) );

        manager.checkMetadata( session, check );
        assertEquals( false, check.isRequired() );
    }

    @Test( expected = IllegalArgumentException.class )
    public void testCheckArtifactFailOnNoFile()
        throws Exception
    {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setItem( artifact.setFile( null ) );
        check.setFile( null );

        manager.checkArtifact( session, check );
        assertNotNull( check.getException() );
    }

    @Test
    public void testCheckArtifactUpdatePolicyRequired()
        throws Exception
    {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setItem( artifact );
        check.setFile( artifact.getFile() );

        Calendar cal = Calendar.getInstance( TimeZone.getTimeZone( "UTC" ) );
        cal.add( Calendar.DATE, -1 );
        long lastUpdate = cal.getTimeInMillis();
        artifact.getFile().setLastModified( lastUpdate );
        check.setLocalLastUpdated( lastUpdate );

        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_ALWAYS );
        manager.checkArtifact( session, check );
        assertNull( check.getException() );
        assertTrue( check.isRequired() );

        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_DAILY );
        manager.checkArtifact( session, check );
        assertNull( check.getException() );
        assertTrue( check.isRequired() );

        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":60" );
        manager.checkArtifact( session, check );
        assertNull( check.getException() );
        assertTrue( check.isRequired() );
    }

    @Test
    public void testCheckArtifactUpdatePolicyNotRequired()
        throws Exception
    {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setItem( artifact );
        check.setFile( artifact.getFile() );

        Calendar cal = Calendar.getInstance( TimeZone.getTimeZone( "UTC" ) );
        cal.add( Calendar.HOUR_OF_DAY, -1 );
        check.setLocalLastUpdated( cal.getTimeInMillis() );

        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_NEVER );
        manager.checkArtifact( session, check );
        assertFalse( check.isRequired() );

        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_DAILY );
        manager.checkArtifact( session, check );
        assertFalse( check.isRequired() );

        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_INTERVAL + ":61" );
        manager.checkArtifact( session, check );
        assertFalse( check.isRequired() );

        check.setPolicy( "no particular policy" );
        manager.checkArtifact( session, check );
        assertFalse( check.isRequired() );
    }

    @Test
    public void testCheckArtifact()
        throws Exception
    {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        long fifteenMinutes = new Date().getTime() - ( 15 * 60 * 1000 );
        check.getFile().setLastModified( fifteenMinutes );
        // time is truncated on setLastModfied
        fifteenMinutes = check.getFile().lastModified();

        // never checked before
        manager.checkArtifact( session, check );
        assertEquals( true, check.isRequired() );

        // just checked
        check.setLocalLastUpdated( 0 );
        long lastUpdate = new Date().getTime();
        check.getFile().setLastModified( lastUpdate );
        lastUpdate = check.getFile().lastModified();

        manager.checkArtifact( session, check );
        assertEquals( false, check.isRequired() );

        // no local file, no repo timestamp
        check.setLocalLastUpdated( 0 );
        check.getFile().delete();
        manager.checkArtifact( session, check );
        assertEquals( true, check.isRequired() );
    }

    @Test
    public void testCheckArtifactNoLocalFile()
        throws Exception
    {
        artifact.getFile().delete();
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();

        long lastUpdate = new Date().getTime() - HOUR;

        // ! file.exists && updateRequired -> check in remote repo
        check.setLocalLastUpdated( lastUpdate );
        manager.checkArtifact( session, check );
        assertEquals( true, check.isRequired() );
    }

    @Test
    public void testCheckArtifactNotFoundInRepoCachingEnabled()
        throws Exception
    {
        artifact.getFile().delete();
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( true, false ) );

        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setException( new ArtifactNotFoundException( artifact, repository ) );
        manager.touchArtifact( session, check );
        resetSessionData( session );

        // ! file.exists && ! updateRequired -> artifact not found in remote repo
        check = newArtifactCheck().setPolicy( RepositoryPolicy.UPDATE_POLICY_DAILY );
        manager.checkArtifact( session, check );
        assertEquals( false, check.isRequired() );
        assertTrue( check.getException() instanceof ArtifactNotFoundException );
    }

    @Test
    public void testCheckArtifactNotFoundInRepoCachingDisabled()
        throws Exception
    {
        artifact.getFile().delete();
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( false, false ) );

        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setException( new ArtifactNotFoundException( artifact, repository ) );
        manager.touchArtifact( session, check );
        resetSessionData( session );

        // ! file.exists && updateRequired -> check in remote repo
        check = newArtifactCheck().setPolicy( RepositoryPolicy.UPDATE_POLICY_DAILY );
        manager.checkArtifact( session, check );
        assertEquals( true, check.isRequired() );
        assertNull( check.getException() );
    }

    @Test
    public void testCheckArtifactErrorFromRepoCachingEnabled()
        throws Exception
    {
        artifact.getFile().delete();

        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_DAILY );
        check.setException( new ArtifactTransferException( artifact, repository, "some error" ) );
        manager.touchArtifact( session, check );
        resetSessionData( session );

        // ! file.exists && ! updateRequired && previousError -> depends on transfer error caching
        check = newArtifactCheck();
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( false, true ) );
        manager.checkArtifact( session, check );
        assertEquals( false, check.isRequired() );
        assertTrue( check.getException() instanceof ArtifactTransferException );
    }

    @Test
    public void testCheckArtifactErrorFromRepoCachingDisabled()
        throws Exception
    {
        artifact.getFile().delete();

        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_DAILY );
        check.setException( new ArtifactTransferException( artifact, repository, "some error" ) );
        manager.touchArtifact( session, check );
        resetSessionData( session );

        // ! file.exists && ! updateRequired && previousError -> depends on transfer error caching
        check = newArtifactCheck();
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( false, false ) );
        manager.checkArtifact( session, check );
        assertEquals( true, check.isRequired() );
        assertNull( check.getException() );
    }

    @Test
    public void testCheckArtifactAtMostOnceDuringSessionEvenIfUpdatePolicyAlways()
        throws Exception
    {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_ALWAYS );

        // first check
        manager.checkArtifact( session, check );
        assertEquals( true, check.isRequired() );

        manager.touchArtifact( session, check );

        // second check in same session
        manager.checkArtifact( session, check );
        assertEquals( false, check.isRequired() );
    }

    @Test
    public void testCheckArtifactAtMostOnceDuringSessionEvenIfUpdatePolicyAlways_InvalidFile()
        throws Exception
    {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_ALWAYS );
        check.setFileValid( false );

        // first check
        manager.checkArtifact( session, check );
        assertEquals( true, check.isRequired() );

        manager.touchArtifact( session, check );

        // second check in same session
        manager.checkArtifact( session, check );
        assertEquals( false, check.isRequired() );
    }

    @Test
    public void testCheckArtifactWhenLocallyMissingEvenIfUpdatePolicyIsNever()
        throws Exception
    {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_NEVER );
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( true, false ) );

        check.getFile().delete();
        assertEquals( check.getFile().getAbsolutePath(), false, check.getFile().exists() );

        manager.checkArtifact( session, check );
        assertEquals( true, check.isRequired() );
    }

    @Test
    public void testCheckArtifactWhenLocallyPresentButInvalidEvenIfUpdatePolicyIsNever()
        throws Exception
    {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_NEVER );
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( true, false ) );

        manager.touchArtifact( session, check );
        resetSessionData( session );

        check.setFileValid( false );

        manager.checkArtifact( session, check );
        assertEquals( true, check.isRequired() );
    }

    @Test
    public void testCheckArtifactWhenLocallyDeletedEvenIfTimestampUpToDate()
        throws Exception
    {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( true, false ) );

        manager.touchArtifact( session, check );
        resetSessionData( session );

        check.getFile().delete();
        assertEquals( check.getFile().getAbsolutePath(), false, check.getFile().exists() );

        manager.checkArtifact( session, check );
        assertEquals( true, check.isRequired() );
    }

    @Test
    public void testCheckArtifactNotWhenUpdatePolicyIsNeverAndTimestampIsUnavailable()
        throws Exception
    {
        UpdateCheck<Artifact, ArtifactTransferException> check = newArtifactCheck();
        check.setPolicy( RepositoryPolicy.UPDATE_POLICY_NEVER );
        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( true, false ) );

        manager.checkArtifact( session, check );
        assertEquals( false, check.isRequired() );
    }

}
