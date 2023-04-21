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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;

import static java.util.Objects.requireNonNull;

/**
 * Collects observed repository events for later inspection.
 */
class RecordingRepositoryListener implements RepositoryListener {

    private final List<RepositoryEvent> events = Collections.synchronizedList(new ArrayList<>());

    public List<RepositoryEvent> getEvents(RepositoryEvent.EventType... types) {
        if (types == null || types.length == 0) {
            return events;
        }
        Set<RepositoryEvent.EventType> requiredTypes = EnumSet.copyOf(Arrays.asList(types));
        return events.stream().filter(e -> requiredTypes.contains(e.getType())).collect(Collectors.toList());
    }

    public void clear() {
        events.clear();
    }

    public void artifactDescriptorInvalid(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void artifactDescriptorMissing(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void metadataInvalid(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void artifactResolving(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void artifactResolved(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void artifactDownloading(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void artifactDownloaded(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void metadataDownloaded(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void metadataDownloading(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void metadataResolving(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void metadataResolved(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void artifactInstalling(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void artifactInstalled(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void metadataInstalling(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void metadataInstalled(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void artifactDeploying(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void artifactDeployed(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void metadataDeploying(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }

    public void metadataDeployed(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");
        events.add(event);
    }
}
