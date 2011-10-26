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

import java.io.File;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.ArtifactRepository;

/**
 * An event describing an action performed by the repository system. Note that events which indicate the end of an
 * action like {@link EventType#ARTIFACT_RESOLVED} are generally fired in both the success and the failure case. Use
 * {@link #getException()} to check whether an event denotes success or failure.
 * @see RepositoryListener
 */
public interface RepositoryEvent
{

    /**
     * The type of the repository event.
     */
    enum EventType
    {
        ARTIFACT_DESCRIPTOR_INVALID,
        ARTIFACT_DESCRIPTOR_MISSING,
        METADATA_INVALID,
        ARTIFACT_RESOLVING,
        ARTIFACT_RESOLVED,
        METADATA_RESOLVING,
        METADATA_RESOLVED,
        ARTIFACT_DOWNLOADING,
        ARTIFACT_DOWNLOADED,
        METADATA_DOWNLOADING,
        METADATA_DOWNLOADED,
        ARTIFACT_INSTALLING,
        ARTIFACT_INSTALLED,
        METADATA_INSTALLING,
        METADATA_INSTALLED,
        ARTIFACT_DEPLOYING,
        ARTIFACT_DEPLOYED,
        METADATA_DEPLOYING,
        METADATA_DEPLOYED
    }

    /**
     * Gets the type of the event.
     * 
     * @return The type of the event, never {@code null}.
     */
    EventType getType();

    /**
     * Gets the repository system session during which the event occurred.
     * 
     * @return The repository system session during which the event occurred, never {@code null}.
     */
    RepositorySystemSession getSession();

    /**
     * Gets the artifact involved in the event (if any).
     * 
     * @return The involved artifact or {@code null} if none.
     */
    Artifact getArtifact();

    /**
     * Gets the metadata involved in the event (if any).
     * 
     * @return The involved metadata or {@code null} if none.
     */
    Metadata getMetadata();

    /**
     * Gets the file involved in the event (if any).
     * 
     * @return The involved file or {@code null} if none.
     */
    File getFile();

    /**
     * Gets the repository involved in the event (if any).
     * 
     * @return The involved repository or {@code null} if none.
     */
    ArtifactRepository getRepository();

    /**
     * Gets the exception that caused the event (if any). As a rule of thumb, an event accompanied by an exception
     * indicates a failure of the corresponding action. If multiple exceptions occurred, this method returns the first
     * exception.
     * 
     * @return The exception or {@code null} if none.
     */
    Exception getException();

    /**
     * Gets the exceptions that caused the event (if any). As a rule of thumb, an event accompanied by exceptions
     * indicates a failure of the corresponding action.
     * 
     * @return The exceptions, never {@code null}.
     */
    List<Exception> getExceptions();

    /**
     * Gets the trace information about the request during which the event occurred.
     * 
     * @return The trace information or {@code null} if none.
     */
    RequestTrace getTrace();

}
