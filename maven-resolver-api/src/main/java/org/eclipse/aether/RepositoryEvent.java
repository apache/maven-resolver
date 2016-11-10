package org.eclipse.aether;

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

import java.io.File;
import java.util.Collections;
import java.util.List;
import static java.util.Objects.requireNonNull;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.ArtifactRepository;

/**
 * An event describing an action performed by the repository system. Note that events which indicate the end of an
 * action like {@link EventType#ARTIFACT_RESOLVED} are generally fired in both the success and the failure case. Use
 * {@link #getException()} to check whether an event denotes success or failure.
 * 
 * @see RepositoryListener
 * @see RepositoryEvent.Builder
 */
public final class RepositoryEvent
{

    /**
     * The type of the repository event.
     */
    public enum EventType
    {

        /**
         * @see RepositoryListener#artifactDescriptorInvalid(RepositoryEvent)
         */
        ARTIFACT_DESCRIPTOR_INVALID,

        /**
         * @see RepositoryListener#artifactDescriptorMissing(RepositoryEvent)
         */
        ARTIFACT_DESCRIPTOR_MISSING,

        /**
         * @see RepositoryListener#metadataInvalid(RepositoryEvent)
         */
        METADATA_INVALID,

        /**
         * @see RepositoryListener#artifactResolving(RepositoryEvent)
         */
        ARTIFACT_RESOLVING,

        /**
         * @see RepositoryListener#artifactResolved(RepositoryEvent)
         */
        ARTIFACT_RESOLVED,

        /**
         * @see RepositoryListener#metadataResolving(RepositoryEvent)
         */
        METADATA_RESOLVING,

        /**
         * @see RepositoryListener#metadataResolved(RepositoryEvent)
         */
        METADATA_RESOLVED,

        /**
         * @see RepositoryListener#artifactDownloading(RepositoryEvent)
         */
        ARTIFACT_DOWNLOADING,

        /**
         * @see RepositoryListener#artifactDownloaded(RepositoryEvent)
         */
        ARTIFACT_DOWNLOADED,

        /**
         * @see RepositoryListener#metadataDownloading(RepositoryEvent)
         */
        METADATA_DOWNLOADING,

        /**
         * @see RepositoryListener#metadataDownloaded(RepositoryEvent)
         */
        METADATA_DOWNLOADED,

        /**
         * @see RepositoryListener#artifactInstalling(RepositoryEvent)
         */
        ARTIFACT_INSTALLING,

        /**
         * @see RepositoryListener#artifactInstalled(RepositoryEvent)
         */
        ARTIFACT_INSTALLED,

        /**
         * @see RepositoryListener#metadataInstalling(RepositoryEvent)
         */
        METADATA_INSTALLING,

        /**
         * @see RepositoryListener#metadataInstalled(RepositoryEvent)
         */
        METADATA_INSTALLED,

        /**
         * @see RepositoryListener#artifactDeploying(RepositoryEvent)
         */
        ARTIFACT_DEPLOYING,

        /**
         * @see RepositoryListener#artifactDeployed(RepositoryEvent)
         */
        ARTIFACT_DEPLOYED,

        /**
         * @see RepositoryListener#metadataDeploying(RepositoryEvent)
         */
        METADATA_DEPLOYING,

        /**
         * @see RepositoryListener#metadataDeployed(RepositoryEvent)
         */
        METADATA_DEPLOYED

    }

    private final EventType type;

    private final RepositorySystemSession session;

    private final Artifact artifact;

    private final Metadata metadata;

    private final ArtifactRepository repository;

    private final File file;

    private final List<Exception> exceptions;

    private final RequestTrace trace;

    RepositoryEvent( Builder builder )
    {
        type = builder.type;
        session = builder.session;
        artifact = builder.artifact;
        metadata = builder.metadata;
        repository = builder.repository;
        file = builder.file;
        exceptions = builder.exceptions;
        trace = builder.trace;
    }

    /**
     * Gets the type of the event.
     * 
     * @return The type of the event, never {@code null}.
     */
    public EventType getType()
    {
        return type;
    }

    /**
     * Gets the repository system session during which the event occurred.
     * 
     * @return The repository system session during which the event occurred, never {@code null}.
     */
    public RepositorySystemSession getSession()
    {
        return session;
    }

    /**
     * Gets the artifact involved in the event (if any).
     * 
     * @return The involved artifact or {@code null} if none.
     */
    public Artifact getArtifact()
    {
        return artifact;
    }

    /**
     * Gets the metadata involved in the event (if any).
     * 
     * @return The involved metadata or {@code null} if none.
     */
    public Metadata getMetadata()
    {
        return metadata;
    }

    /**
     * Gets the file involved in the event (if any).
     * 
     * @return The involved file or {@code null} if none.
     */
    public File getFile()
    {
        return file;
    }

    /**
     * Gets the repository involved in the event (if any).
     * 
     * @return The involved repository or {@code null} if none.
     */
    public ArtifactRepository getRepository()
    {
        return repository;
    }

    /**
     * Gets the exception that caused the event (if any). As a rule of thumb, an event accompanied by an exception
     * indicates a failure of the corresponding action. If multiple exceptions occurred, this method returns the first
     * exception.
     * 
     * @return The exception or {@code null} if none.
     */
    public Exception getException()
    {
        return exceptions.isEmpty() ? null : exceptions.get( 0 );
    }

    /**
     * Gets the exceptions that caused the event (if any). As a rule of thumb, an event accompanied by exceptions
     * indicates a failure of the corresponding action.
     * 
     * @return The exceptions, never {@code null}.
     */
    public List<Exception> getExceptions()
    {
        return exceptions;
    }

    /**
     * Gets the trace information about the request during which the event occurred.
     * 
     * @return The trace information or {@code null} if none.
     */
    public RequestTrace getTrace()
    {
        return trace;
    }

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder( 256 );
        buffer.append( getType() );
        if ( getArtifact() != null )
        {
            buffer.append( " " ).append( getArtifact() );
        }
        if ( getMetadata() != null )
        {
            buffer.append( " " ).append( getMetadata() );
        }
        if ( getFile() != null )
        {
            buffer.append( " (" ).append( getFile() ).append( ")" );
        }
        if ( getRepository() != null )
        {
            buffer.append( " @ " ).append( getRepository() );
        }
        return buffer.toString();
    }

    /**
     * A builder to create events.
     */
    public static final class Builder
    {

        EventType type;

        RepositorySystemSession session;

        Artifact artifact;

        Metadata metadata;

        ArtifactRepository repository;

        File file;

        List<Exception> exceptions = Collections.emptyList();

        RequestTrace trace;

        /**
         * Creates a new event builder for the specified session and event type.
         *
         * @param session The repository system session, must not be {@code null}.
         * @param type The type of the event, must not be {@code null}.
         */
        public Builder( RepositorySystemSession session, EventType type )
        {
            this.session = requireNonNull( session, "session cannot be null" );
            this.type = requireNonNull( type, "event type cannot be null" );
        }

        /**
         * Sets the artifact involved in the event.
         *
         * @param artifact The involved artifact, may be {@code null}.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder setArtifact( Artifact artifact )
        {
            this.artifact = artifact;
            return this;
        }

        /**
         * Sets the metadata involved in the event.
         * 
         * @param metadata The involved metadata, may be {@code null}.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder setMetadata( Metadata metadata )
        {
            this.metadata = metadata;
            return this;
        }

        /**
         * Sets the repository involved in the event.
         * 
         * @param repository The involved repository, may be {@code null}.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder setRepository( ArtifactRepository repository )
        {
            this.repository = repository;
            return this;
        }

        /**
         * Sets the file involved in the event.
         * 
         * @param file The involved file, may be {@code null}.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder setFile( File file )
        {
            this.file = file;
            return this;
        }

        /**
         * Sets the exception causing the event.
         * 
         * @param exception The exception causing the event, may be {@code null}.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder setException( Exception exception )
        {
            if ( exception != null )
            {
                this.exceptions = Collections.singletonList( exception );
            }
            else
            {
                this.exceptions = Collections.emptyList();
            }
            return this;
        }

        /**
         * Sets the exceptions causing the event.
         * 
         * @param exceptions The exceptions causing the event, may be {@code null}.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder setExceptions( List<Exception> exceptions )
        {
            if ( exceptions != null )
            {
                this.exceptions = exceptions;
            }
            else
            {
                this.exceptions = Collections.emptyList();
            }
            return this;
        }

        /**
         * Sets the trace information about the request during which the event occurred.
         * 
         * @param trace The trace information, may be {@code null}.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder setTrace( RequestTrace trace )
        {
            this.trace = trace;
            return this;
        }

        /**
         * Builds a new event from the current values of this builder. The state of the builder itself remains
         * unchanged.
         * 
         * @return The event, never {@code null}.
         */
        public RepositoryEvent build()
        {
            return new RepositoryEvent( this );
        }

    }

}
