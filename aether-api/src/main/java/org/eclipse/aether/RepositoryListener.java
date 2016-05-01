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
package org.eclipse.aether;

/**
 * A listener being notified of events from the repository system. In general, the system sends events upon termination
 * of an operation like {@link #artifactResolved(RepositoryEvent)} regardless whether it succeeded or failed so
 * listeners need to inspect the event details carefully. Also, the listener may be called from an arbitrary thread.
 * <em>Note:</em> Implementors are strongly advised to inherit from {@link AbstractRepositoryListener} instead of
 * directly implementing this interface.
 * 
 * @see org.eclipse.aether.RepositorySystemSession#getRepositoryListener()
 * @see org.eclipse.aether.transfer.TransferListener
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface RepositoryListener
{

    /**
     * Notifies the listener of a syntactically or semantically invalid artifact descriptor.
     * {@link RepositoryEvent#getArtifact()} indicates the artifact whose descriptor is invalid and
     * {@link RepositoryEvent#getExceptions()} carries the encountered errors. Depending on the session's
     * {@link org.eclipse.aether.resolution.ArtifactDescriptorPolicy}, the underlying repository operation might abort
     * with an exception or ignore the invalid descriptor.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void artifactDescriptorInvalid( RepositoryEvent event );

    /**
     * Notifies the listener of a missing artifact descriptor. {@link RepositoryEvent#getArtifact()} indicates the
     * artifact whose descriptor is missing. Depending on the session's
     * {@link org.eclipse.aether.resolution.ArtifactDescriptorPolicy}, the underlying repository operation might abort
     * with an exception or ignore the missing descriptor.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void artifactDescriptorMissing( RepositoryEvent event );

    /**
     * Notifies the listener of syntactically or semantically invalid metadata. {@link RepositoryEvent#getMetadata()}
     * indicates the invalid metadata and {@link RepositoryEvent#getExceptions()} carries the encountered errors. The
     * underlying repository operation might still succeed, depending on whether the metadata in question is actually
     * needed to carry out the resolution process.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void metadataInvalid( RepositoryEvent event );

    /**
     * Notifies the listener of an artifact that is about to be resolved. {@link RepositoryEvent#getArtifact()} denotes
     * the artifact in question. Unlike the {@link #artifactDownloading(RepositoryEvent)} event, this event is fired
     * regardless whether the artifact already exists locally or not.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void artifactResolving( RepositoryEvent event );

    /**
     * Notifies the listener of an artifact whose resolution has been completed, either successfully or not.
     * {@link RepositoryEvent#getArtifact()} denotes the artifact in question and
     * {@link RepositoryEvent#getExceptions()} indicates whether the resolution succeeded or failed. Unlike the
     * {@link #artifactDownloaded(RepositoryEvent)} event, this event is fired regardless whether the artifact already
     * exists locally or not.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void artifactResolved( RepositoryEvent event );

    /**
     * Notifies the listener of some metadata that is about to be resolved. {@link RepositoryEvent#getMetadata()}
     * denotes the metadata in question. Unlike the {@link #metadataDownloading(RepositoryEvent)} event, this event is
     * fired regardless whether the metadata already exists locally or not.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void metadataResolving( RepositoryEvent event );

    /**
     * Notifies the listener of some metadata whose resolution has been completed, either successfully or not.
     * {@link RepositoryEvent#getMetadata()} denotes the metadata in question and
     * {@link RepositoryEvent#getExceptions()} indicates whether the resolution succeeded or failed. Unlike the
     * {@link #metadataDownloaded(RepositoryEvent)} event, this event is fired regardless whether the metadata already
     * exists locally or not.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void metadataResolved( RepositoryEvent event );

    /**
     * Notifies the listener of an artifact that is about to be downloaded from a remote repository.
     * {@link RepositoryEvent#getArtifact()} denotes the artifact in question and
     * {@link RepositoryEvent#getRepository()} the source repository. Unlike the
     * {@link #artifactResolving(RepositoryEvent)} event, this event is only fired when the artifact does not already
     * exist locally.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void artifactDownloading( RepositoryEvent event );

    /**
     * Notifies the listener of an artifact whose download has been completed, either successfully or not.
     * {@link RepositoryEvent#getArtifact()} denotes the artifact in question and
     * {@link RepositoryEvent#getExceptions()} indicates whether the download succeeded or failed. Unlike the
     * {@link #artifactResolved(RepositoryEvent)} event, this event is only fired when the artifact does not already
     * exist locally.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void artifactDownloaded( RepositoryEvent event );

    /**
     * Notifies the listener of some metadata that is about to be downloaded from a remote repository.
     * {@link RepositoryEvent#getMetadata()} denotes the metadata in question and
     * {@link RepositoryEvent#getRepository()} the source repository. Unlike the
     * {@link #metadataResolving(RepositoryEvent)} event, this event is only fired when the metadata does not already
     * exist locally.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void metadataDownloading( RepositoryEvent event );

    /**
     * Notifies the listener of some metadata whose download has been completed, either successfully or not.
     * {@link RepositoryEvent#getMetadata()} denotes the metadata in question and
     * {@link RepositoryEvent#getExceptions()} indicates whether the download succeeded or failed. Unlike the
     * {@link #metadataResolved(RepositoryEvent)} event, this event is only fired when the metadata does not already
     * exist locally.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void metadataDownloaded( RepositoryEvent event );

    /**
     * Notifies the listener of an artifact that is about to be installed to the local repository.
     * {@link RepositoryEvent#getArtifact()} denotes the artifact in question.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void artifactInstalling( RepositoryEvent event );

    /**
     * Notifies the listener of an artifact whose installation to the local repository has been completed, either
     * successfully or not. {@link RepositoryEvent#getArtifact()} denotes the artifact in question and
     * {@link RepositoryEvent#getExceptions()} indicates whether the installation succeeded or failed.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void artifactInstalled( RepositoryEvent event );

    /**
     * Notifies the listener of some metadata that is about to be installed to the local repository.
     * {@link RepositoryEvent#getMetadata()} denotes the metadata in question.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void metadataInstalling( RepositoryEvent event );

    /**
     * Notifies the listener of some metadata whose installation to the local repository has been completed, either
     * successfully or not. {@link RepositoryEvent#getMetadata()} denotes the metadata in question and
     * {@link RepositoryEvent#getExceptions()} indicates whether the installation succeeded or failed.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void metadataInstalled( RepositoryEvent event );

    /**
     * Notifies the listener of an artifact that is about to be uploaded to a remote repository.
     * {@link RepositoryEvent#getArtifact()} denotes the artifact in question and
     * {@link RepositoryEvent#getRepository()} the destination repository.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void artifactDeploying( RepositoryEvent event );

    /**
     * Notifies the listener of an artifact whose upload to a remote repository has been completed, either successfully
     * or not. {@link RepositoryEvent#getArtifact()} denotes the artifact in question and
     * {@link RepositoryEvent#getExceptions()} indicates whether the upload succeeded or failed.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void artifactDeployed( RepositoryEvent event );

    /**
     * Notifies the listener of some metadata that is about to be uploaded to a remote repository.
     * {@link RepositoryEvent#getMetadata()} denotes the metadata in question and
     * {@link RepositoryEvent#getRepository()} the destination repository.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void metadataDeploying( RepositoryEvent event );

    /**
     * Notifies the listener of some metadata whose upload to a remote repository has been completed, either
     * successfully or not. {@link RepositoryEvent#getMetadata()} denotes the metadata in question and
     * {@link RepositoryEvent#getExceptions()} indicates whether the upload succeeded or failed.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void metadataDeployed( RepositoryEvent event );

}
