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
package org.eclipse.aether.internal.impl;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.impl.RepositoryEventDispatcher;

import static java.util.Objects.requireNonNull;

/**
 */
public class StubRepositoryEventDispatcher implements RepositoryEventDispatcher {

    public void dispatch(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        RepositoryListener listener = event.getSession().getRepositoryListener();
        if (listener == null) {
            return;
        }

        switch (event.getType()) {
            case ARTIFACT_DEPLOYED:
                listener.artifactDeployed(event);
                break;
            case ARTIFACT_DEPLOYING:
                listener.artifactDeploying(event);
                break;
            case ARTIFACT_DESCRIPTOR_INVALID:
                listener.artifactDescriptorInvalid(event);
                break;
            case ARTIFACT_DESCRIPTOR_MISSING:
                listener.artifactDescriptorMissing(event);
                break;
            case ARTIFACT_DOWNLOADED:
                listener.artifactDownloaded(event);
                break;
            case ARTIFACT_DOWNLOADING:
                listener.artifactDownloading(event);
                break;
            case ARTIFACT_INSTALLED:
                listener.artifactInstalled(event);
                break;
            case ARTIFACT_INSTALLING:
                listener.artifactInstalling(event);
                break;
            case ARTIFACT_RESOLVED:
                listener.artifactResolved(event);
                break;
            case ARTIFACT_RESOLVING:
                listener.artifactResolving(event);
                break;
            case METADATA_DEPLOYED:
                listener.metadataDeployed(event);
                break;
            case METADATA_DEPLOYING:
                listener.metadataDeploying(event);
                break;
            case METADATA_DOWNLOADED:
                listener.metadataDownloaded(event);
                break;
            case METADATA_DOWNLOADING:
                listener.metadataDownloading(event);
                break;
            case METADATA_INSTALLED:
                listener.metadataInstalled(event);
                break;
            case METADATA_INSTALLING:
                listener.metadataInstalling(event);
                break;
            case METADATA_INVALID:
                listener.metadataInvalid(event);
                break;
            case METADATA_RESOLVED:
                listener.metadataResolved(event);
                break;
            case METADATA_RESOLVING:
                listener.metadataResolving(event);
                break;
            default:
                throw new IllegalStateException("unknown repository event type " + event.getType());
        }
    }
}
