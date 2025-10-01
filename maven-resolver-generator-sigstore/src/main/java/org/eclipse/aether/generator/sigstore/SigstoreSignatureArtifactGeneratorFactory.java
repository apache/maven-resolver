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
package org.eclipse.aether.generator.sigstore;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Collection;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.spi.artifact.ArtifactPredicateFactory;
import org.eclipse.aether.spi.artifact.generator.ArtifactGenerator;
import org.eclipse.aether.spi.artifact.generator.ArtifactGeneratorFactory;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.util.ConfigUtils;

@Singleton
@Named(SigstoreSignatureArtifactGeneratorFactory.NAME)
public final class SigstoreSignatureArtifactGeneratorFactory implements ArtifactGeneratorFactory {

    public static final String NAME = "sigstore";

    private final ArtifactPredicateFactory artifactPredicateFactory;
    private final PathProcessor pathProcessor;

    @Inject
    public SigstoreSignatureArtifactGeneratorFactory(
            ArtifactPredicateFactory artifactPredicateFactory, PathProcessor pathProcessor) {
        this.artifactPredicateFactory = artifactPredicateFactory;
        this.pathProcessor = pathProcessor;
    }

    @Override
    public ArtifactGenerator newInstance(RepositorySystemSession session, InstallRequest request) {
        return null; // nothing on install
    }

    @Override
    public ArtifactGenerator newInstance(RepositorySystemSession session, DeployRequest request) {
        return newInstance(session, request.getArtifacts()); // generate on deploy
    }

    private ArtifactGenerator newInstance(RepositorySystemSession session, Collection<Artifact> artifacts) {
        final boolean enabled = ConfigUtils.getBoolean(
                session, SigstoreConfigurationKeys.DEFAULT_ENABLED, SigstoreConfigurationKeys.CONFIG_PROP_ENABLED);
        if (!enabled) {
            return null;
        }
        final boolean publicStaging = ConfigUtils.getBoolean(
                session,
                SigstoreConfigurationKeys.DEFAULT_PUBLIC_STAGING,
                SigstoreConfigurationKeys.CONFIG_PROP_PUBLIC_STAGING);

        return new SigstoreSignatureArtifactGenerator(
                pathProcessor, artifacts, artifactPredicateFactory.newInstance(session)::hasChecksums, publicStaging);
    }

    @Override
    public float getPriority() {
        return 150;
    }
}
