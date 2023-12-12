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
package org.eclipse.aether.internal.impl.relocation;

import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.spi.relocation.ArtifactRelocationSource;
import org.eclipse.aether.util.ConfigUtils;

/**
 * An implementation of {@link ArtifactRelocationSource} that uses plain Java properties file to read relocations from.
 *
 * @since 2.0.0
 */
@Singleton
@Named
public final class PropertyFileArtifactRelocationSource implements ArtifactRelocationSource {

    public static final String NAME = "simpleProperties";

    public static final String CONFIG_PROPS_PREFIX =
            ConfigurationProperties.PREFIX_AETHER + "artifactRelocationSource." + NAME + ".";

    /**
     * Configuration property to pass in location of properties file containing relocations. Properties file is expected
     * to be in form {@code GAV=GAV}, where "GAV" should be the standard string in form of
     * {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}, that is a textual representation of
     * artifact coordinates.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     */
    public static final String CONFIG_PROP_RELOCATIONS_FILE = CONFIG_PROPS_PREFIX + "relocationsFile";

    private static final String PROPERTY_KEY = PropertyFileArtifactRelocationSource.class.getName() + ".properties";

    private float priority;

    @Override
    public float getPriority() {
        return priority;
    }

    /**
     * Sets the priority of this component.
     *
     * @param priority The priority.
     * @return This component for chaining, never {@code null}.
     */
    public PropertyFileArtifactRelocationSource setPriority(float priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public Artifact relocatedTarget(RepositorySystemSession session, Artifact artifact) {
        Properties relocations = (Properties) session.getData().computeIfAbsent(PROPERTY_KEY, () -> {
            Properties properties = new Properties();
            String relocationsFile = ConfigUtils.getString(session, null, CONFIG_PROP_RELOCATIONS_FILE);
            if (relocationsFile != null) {
                Path path = Paths.get(relocationsFile);
                if (Files.isReadable(path)) {
                    try (InputStream inputStream = Files.newInputStream(path)) {
                        properties.load(inputStream);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
            return properties;
        });
        String target = relocations.getProperty(artifact.toString());
        if (target != null) {
            return new DefaultArtifact(target);
        }
        return null;
    }
}
