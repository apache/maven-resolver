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
package org.eclipse.aether.util.artifact;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.aether.artifact.AbstractArtifact;
import org.eclipse.aether.artifact.Artifact;

import static java.util.Objects.requireNonNull;

/**
 * An artifact that delegates to another artifact instance. This class serves as a base for subclasses that want to
 * carry additional data fields.
 */
public abstract class DelegatingArtifact extends AbstractArtifact {

    private final Artifact delegate;

    /**
     * Creates a new artifact instance that delegates to the specified artifact.
     *
     * @param delegate The artifact to delegate to, must not be {@code null}.
     */
    protected DelegatingArtifact(Artifact delegate) {
        this.delegate = requireNonNull(delegate, "delegate artifact cannot be null");
    }

    /**
     * Creates a new artifact instance that delegates to the specified artifact. Subclasses should use this hook to
     * instantiate themselves, taking along any data from the current instance that was added.
     *
     * @param delegate The artifact to delegate to, must not be {@code null}.
     * @return The new delegating artifact, never {@code null}.
     */
    protected abstract DelegatingArtifact newInstance(Artifact delegate);

    @Override
    public String getGroupId() {
        return delegate.getGroupId();
    }

    @Override
    public String getArtifactId() {
        return delegate.getArtifactId();
    }

    @Override
    public String getVersion() {
        return delegate.getVersion();
    }

    @Override
    public Artifact setVersion(String version) {
        Artifact artifact = delegate.setVersion(version);
        if (artifact != delegate) {
            return newInstance(artifact);
        }
        return this;
    }

    @Override
    public String getBaseVersion() {
        return delegate.getBaseVersion();
    }

    @Override
    public boolean isSnapshot() {
        return delegate.isSnapshot();
    }

    @Override
    public String getClassifier() {
        return delegate.getClassifier();
    }

    @Override
    public String getExtension() {
        return delegate.getExtension();
    }

    @Deprecated
    @Override
    public File getFile() {
        return delegate.getFile();
    }

    @Override
    public Path getPath() {
        return delegate.getPath();
    }

    @Deprecated
    @Override
    public Artifact setFile(File file) {
        Artifact artifact = delegate.setFile(file);
        if (artifact != delegate) {
            return newInstance(artifact);
        }
        return this;
    }

    @Override
    public Artifact setPath(Path path) {
        Artifact artifact = delegate.setPath(path);
        if (artifact != delegate) {
            return newInstance(artifact);
        }
        return this;
    }

    public String getProperty(String key, String defaultValue) {
        return delegate.getProperty(key, defaultValue);
    }

    public Map<String, String> getProperties() {
        return delegate.getProperties();
    }

    public Artifact setProperties(Map<String, String> properties) {
        Artifact artifact = delegate.setProperties(properties);
        if (artifact != delegate) {
            return newInstance(artifact);
        }
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof DelegatingArtifact) {
            return delegate.equals(((DelegatingArtifact) obj).delegate);
        }

        return delegate.equals(obj);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
