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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.RepositorySystemValidator;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.spi.validator.Validator;
import org.eclipse.aether.spi.validator.ValidatorFactory;

import static java.util.Objects.requireNonNull;

@Singleton
@Named
public class DefaultRepositorySystemValidator implements RepositorySystemValidator {
    private final List<ValidatorFactory> validatorFactories;

    @Inject
    public DefaultRepositorySystemValidator(List<ValidatorFactory> validatorFactories) {
        this.validatorFactories = requireNonNull(validatorFactories, "validatorFactories cannot be null");
    }

    private void mayThrow(List<Exception> exceptions, String message) {
        if (!exceptions.isEmpty()) {
            IllegalArgumentException result = new IllegalArgumentException(message);
            exceptions.forEach(result::addSuppressed);
            throw result;
        }
    }

    @Override
    public void validateVersionRequest(RepositorySystemSession session, VersionRequest request) {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (ValidatorFactory factory : validatorFactories) {
            Validator validator = factory.newInstance(session);
            try {
                validator.isValidArtifact(request.getArtifact());
            } catch (Exception e) {
                exceptions.add(e);
            }
            for (RemoteRepository repository : request.getRepositories()) {
                try {
                    validator.isValidRemoteRepository(repository);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
        }
        mayThrow(exceptions, "Invalid Version Request: " + request);
    }

    @Override
    public void validateVersionRangeRequest(RepositorySystemSession session, VersionRangeRequest request) {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (ValidatorFactory factory : validatorFactories) {
            Validator validator = factory.newInstance(session);
            try {
                validator.isValidArtifact(request.getArtifact());
            } catch (Exception e) {
                exceptions.add(e);
            }
            for (RemoteRepository repository : request.getRepositories()) {
                try {
                    validator.isValidRemoteRepository(repository);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
        }
        mayThrow(exceptions, "Invalid Version Range Request: " + request);
    }

    @Override
    public void validateArtifactDescriptorRequest(RepositorySystemSession session, ArtifactDescriptorRequest request) {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (ValidatorFactory factory : validatorFactories) {
            Validator validator = factory.newInstance(session);
            try {
                validator.isValidArtifact(request.getArtifact());
            } catch (Exception e) {
                exceptions.add(e);
            }
            for (RemoteRepository repository : request.getRepositories()) {
                try {
                    validator.isValidRemoteRepository(repository);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
        }
        mayThrow(exceptions, "Invalid Artifact Descriptor Request: " + request);
    }

    @Override
    public void validateArtifactRequests(
            RepositorySystemSession session, Collection<? extends ArtifactRequest> requests) {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (ValidatorFactory factory : validatorFactories) {
            Validator validator = factory.newInstance(session);
            for (ArtifactRequest request : requests) {
                try {
                    validator.isValidArtifact(request.getArtifact());
                } catch (Exception e) {
                    exceptions.add(e);
                }
                for (RemoteRepository repository : request.getRepositories()) {
                    try {
                        validator.isValidRemoteRepository(repository);
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                }
            }
        }
        mayThrow(exceptions, "Invalid Artifact Requests: " + requests);
    }

    @Override
    public void validateMetadataRequests(
            RepositorySystemSession session, Collection<? extends MetadataRequest> requests) {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (ValidatorFactory factory : validatorFactories) {
            Validator validator = factory.newInstance(session);
            for (MetadataRequest request : requests) {
                try {
                    validator.isValidMetadata(request.getMetadata());
                } catch (Exception e) {
                    exceptions.add(e);
                }
                try {
                    validator.isValidRemoteRepository(request.getRepository());
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
        }
        mayThrow(exceptions, "Invalid Metadata Requests: " + requests);
    }

    @Override
    public void validateCollectRequest(RepositorySystemSession session, CollectRequest request) {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (ValidatorFactory factory : validatorFactories) {
            Validator validator = factory.newInstance(session);
            if (request.getRootArtifact() != null) {
                try {
                    validator.isValidArtifact(request.getRootArtifact());
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
            if (request.getRoot() != null) {
                try {
                    validator.isValidDependency(request.getRoot());
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
            for (Dependency dependency : request.getDependencies()) {
                try {
                    validator.isValidDependency(dependency);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
            for (Dependency managedDependency : request.getManagedDependencies()) {
                try {
                    validator.isValidDependency(managedDependency);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
            for (RemoteRepository repository : request.getRepositories()) {
                try {
                    validator.isValidRemoteRepository(repository);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
        }
        mayThrow(exceptions, "Invalid Collect Request: " + request);
    }

    @Override
    public void validateDependencyRequest(RepositorySystemSession session, DependencyRequest request) {
        if (request.getCollectRequest() != null) {
            try {
                validateCollectRequest(session, request.getCollectRequest());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid Dependency Request: " + request, e);
            }
        }
    }

    @Override
    public void validateInstallRequest(RepositorySystemSession session, InstallRequest request) {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (ValidatorFactory factory : validatorFactories) {
            Validator validator = factory.newInstance(session);
            for (Artifact artifact : request.getArtifacts()) {
                try {
                    validator.isValidArtifact(artifact);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
            for (Metadata metadata : request.getMetadata()) {
                try {
                    validator.isValidMetadata(metadata);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
        }
        mayThrow(exceptions, "Invalid Install Request: " + request);
    }

    @Override
    public void validateDeployRequest(RepositorySystemSession session, DeployRequest request) {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (ValidatorFactory factory : validatorFactories) {
            Validator validator = factory.newInstance(session);
            for (Artifact artifact : request.getArtifacts()) {
                try {
                    validator.isValidArtifact(artifact);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
            for (Metadata metadata : request.getMetadata()) {
                try {
                    validator.isValidMetadata(metadata);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
            try {
                validator.isValidRemoteRepository(request.getRepository());
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        mayThrow(exceptions, "Invalid Deploy Request: " + request);
    }

    @Override
    public void validateLocalRepositories(RepositorySystemSession session, Collection<LocalRepository> repositories) {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (ValidatorFactory factory : validatorFactories) {
            Validator validator = factory.newInstance(session);
            for (LocalRepository repository : repositories) {
                try {
                    validator.isValidLocalRepository(repository);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
        }
        mayThrow(exceptions, "Invalid LocalRepositories: " + repositories);
    }

    @Override
    public void validateRemoteRepositories(RepositorySystemSession session, Collection<RemoteRepository> repositories) {
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (ValidatorFactory factory : validatorFactories) {
            Validator validator = factory.newInstance(session);
            for (RemoteRepository repository : repositories) {
                try {
                    validator.isValidRemoteRepository(repository);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
        }
        mayThrow(exceptions, "Invalid RemoteRepositories: " + repositories);
    }
}
