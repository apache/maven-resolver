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
package org.eclipse.aether.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

import static java.util.Objects.requireNonNull;

/**
 * A node within a dependency graph.
 */
public final class DefaultDependencyNode implements DependencyNode {

    private List<DependencyNode> children;

    private Dependency dependency;

    private Artifact artifact;

    private List<? extends Artifact> relocations;

    private Collection<? extends Artifact> aliases;

    private VersionConstraint versionConstraint;

    private Version version;

    private Map<DependencyManagementSubject, Boolean> managedSubjects = Collections.emptyMap();

    private List<RemoteRepository> repositories;

    private String context;

    private Map<Object, Object> data;

    /**
     * Creates a new node with the specified dependency.
     *
     * @param dependency The dependency associated with this node, may be {@code null} for a root node.
     */
    public DefaultDependencyNode(Dependency dependency) {
        this.dependency = dependency;
        artifact = (dependency != null) ? dependency.getArtifact() : null;
        children = new ArrayList<>(0);
        aliases = Collections.emptyList();
        relocations = Collections.emptyList();
        repositories = Collections.emptyList();
        context = "";
        data = Collections.emptyMap();
    }

    /**
     * Creates a new root node with the specified artifact as its label. Note that the new node has no dependency, i.e.
     * {@link #getDependency()} will return {@code null}. Put differently, the specified artifact will not be subject to
     * dependency collection/resolution.
     *
     * @param artifact The artifact to use as label for this node, may be {@code null}.
     */
    public DefaultDependencyNode(Artifact artifact) {
        this.artifact = artifact;
        children = new ArrayList<>(0);
        aliases = Collections.emptyList();
        relocations = Collections.emptyList();
        repositories = Collections.emptyList();
        context = "";
        data = Collections.emptyMap();
    }

    /**
     * Creates a mostly shallow clone of the specified node. The new node has its own copy of any custom data and
     * initially no children.
     *
     * @param node The node to copy, must not be {@code null}.
     */
    public DefaultDependencyNode(DependencyNode node) {
        dependency = node.getDependency();
        artifact = node.getArtifact();
        children = new ArrayList<>(0);
        setAliases(node.getAliases());
        setRequestContext(node.getRequestContext());

        HashMap<DependencyManagementSubject, Boolean> managedSubjects = new HashMap<>();
        for (DependencyManagementSubject subject : DependencyManagementSubject.values()) {
            if (node.isManagedSubject(subject)) {
                managedSubjects.put(subject, node.isManagedSubjectEnforced(subject));
            }
        }
        if (managedSubjects.isEmpty()) {
            setManagedSubjects(null);
        } else {
            setManagedSubjects(managedSubjects);
        }

        setRelocations(node.getRelocations());
        setRepositories(node.getRepositories());
        setVersion(node.getVersion());
        setVersionConstraint(node.getVersionConstraint());
        Map<?, ?> data = node.getData();
        setData(data.isEmpty() ? null : new HashMap<>(data));
    }

    @Override
    public List<DependencyNode> getChildren() {
        return children;
    }

    @Override
    public void setChildren(List<DependencyNode> children) {
        if (children == null) {
            this.children = new ArrayList<>(0);
        } else {
            this.children = children;
        }
    }

    @Override
    public Dependency getDependency() {
        return dependency;
    }

    @Override
    public Artifact getArtifact() {
        return artifact;
    }

    @Override
    public void setArtifact(Artifact artifact) {
        if (dependency == null) {
            throw new IllegalStateException("node does not have a dependency");
        }
        dependency = dependency.setArtifact(artifact);
        this.artifact = dependency.getArtifact();
    }

    @Override
    public List<? extends Artifact> getRelocations() {
        return relocations;
    }

    /**
     * Sets the sequence of relocations that was followed to resolve this dependency's artifact.
     *
     * @param relocations The sequence of relocations, may be {@code null}.
     */
    public void setRelocations(List<? extends Artifact> relocations) {
        if (relocations == null || relocations.isEmpty()) {
            this.relocations = Collections.emptyList();
        } else {
            this.relocations = relocations;
        }
    }

    @Override
    public Collection<? extends Artifact> getAliases() {
        return aliases;
    }

    /**
     * Sets the known aliases for this dependency's artifact.
     *
     * @param aliases The known aliases, may be {@code null}.
     */
    public void setAliases(Collection<? extends Artifact> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            this.aliases = Collections.emptyList();
        } else {
            this.aliases = aliases;
        }
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return versionConstraint;
    }

    /**
     * Sets the version constraint that was parsed from the dependency's version declaration.
     *
     * @param versionConstraint The version constraint for this node, may be {@code null}.
     */
    public void setVersionConstraint(VersionConstraint versionConstraint) {
        this.versionConstraint = versionConstraint;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    /**
     * Sets the version that was selected for the dependency's target artifact.
     *
     * @param version The parsed version, may be {@code null}.
     */
    public void setVersion(Version version) {
        this.version = version;
    }

    @Override
    public void setScope(String scope) {
        if (dependency == null) {
            throw new IllegalStateException("node does not have a dependency");
        }
        dependency = dependency.setScope(scope);
    }

    @Override
    public void setOptional(Boolean optional) {
        if (dependency == null) {
            throw new IllegalStateException("node does not have a dependency");
        }
        dependency = dependency.setOptional(optional);
    }

    @Override
    public int getManagedBits() {
        byte res = 0;
        if (isManagedSubject(DependencyManagementSubject.VERSION)) {
            res |= DependencyNode.MANAGED_VERSION;
        }
        if (isManagedSubject(DependencyManagementSubject.SCOPE)) {
            res |= DependencyNode.MANAGED_SCOPE;
        }
        if (isManagedSubject(DependencyManagementSubject.OPTIONAL)) {
            res |= DependencyNode.MANAGED_OPTIONAL;
        }
        if (isManagedSubject(DependencyManagementSubject.PROPERTIES)) {
            res |= DependencyNode.MANAGED_PROPERTIES;
        }
        if (isManagedSubject(DependencyManagementSubject.EXCLUSIONS)) {
            res |= DependencyNode.MANAGED_EXCLUSIONS;
        }
        return res;
    }

    @Deprecated
    public void setManagedBits(int managedBits) {
        throw new IllegalArgumentException("bits are not supported");
    }

    public void setManagedSubjects(Map<DependencyManagementSubject, Boolean> managedSubjects) {
        if (managedSubjects == null) {
            this.managedSubjects = Collections.emptyMap();
        } else {
            this.managedSubjects = managedSubjects;
        }
    }

    @Override
    public boolean isManagedSubject(DependencyManagementSubject subject) {
        return managedSubjects.containsKey(subject);
    }

    @Override
    public boolean isManagedSubjectEnforced(DependencyManagementSubject subject) {
        return managedSubjects.getOrDefault(subject, false);
    }

    @Override
    public List<RemoteRepository> getRepositories() {
        return repositories;
    }

    /**
     * Sets the remote repositories from which this node's artifact shall be resolved.
     *
     * @param repositories The remote repositories to use for artifact resolution, may be {@code null}.
     */
    public void setRepositories(List<RemoteRepository> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            this.repositories = Collections.emptyList();
        } else {
            this.repositories = repositories;
        }
    }

    @Override
    public String getRequestContext() {
        return context;
    }

    @Override
    public void setRequestContext(String context) {
        this.context = (context != null) ? context.intern() : "";
    }

    @Override
    public Map<Object, Object> getData() {
        return data;
    }

    @Override
    public void setData(Map<Object, Object> data) {
        if (data == null) {
            this.data = Collections.emptyMap();
        } else {
            this.data = data;
        }
    }

    @Override
    public void setData(Object key, Object value) {
        requireNonNull(key, "key cannot be null");

        if (value == null) {
            if (!data.isEmpty()) {
                data.remove(key);

                if (data.isEmpty()) {
                    data = Collections.emptyMap();
                }
            }
        } else {
            if (data.isEmpty()) {
                data = new HashMap<>(1, 2); // nodes can be numerous so let's be space conservative
            }
            data.put(key, value);
        }
    }

    @Override
    public boolean accept(DependencyVisitor visitor) {
        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException(new InterruptedException("Thread interrupted"));
        }
        if (visitor.visitEnter(this)) {
            for (DependencyNode child : children) {
                if (!child.accept(visitor)) {
                    break;
                }
            }
        }

        return visitor.visitLeave(this);
    }

    @Override
    public String toString() {
        Dependency dep = getDependency();
        if (dep == null) {
            return String.valueOf(getArtifact());
        }
        return dep.toString();
    }
}
