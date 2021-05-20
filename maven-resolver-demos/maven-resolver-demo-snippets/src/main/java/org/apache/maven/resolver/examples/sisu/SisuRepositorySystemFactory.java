package org.apache.maven.resolver.examples.sisu;

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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import com.google.inject.Guice;
import com.google.inject.Module;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.sisu.launch.Main;
import org.eclipse.sisu.space.BeanScanning;

/**
 * A factory for repository system instances that employs Eclipse Sisu to wire up the system's components.
 */
@Named
public class SisuRepositorySystemFactory
{
    @Inject
    private RepositorySystem repositorySystem;

    public static RepositorySystem newRepositorySystem()
    {
        final Module app = Main.wire(
            BeanScanning.INDEX,
            new SisuRepositorySystemDemoModule()
        );
        return Guice.createInjector( app ).getInstance( SisuRepositorySystemFactory.class ).repositorySystem;
    }

    @Named
    private static class ModelBuilderProvider
        implements Provider<ModelBuilder>
    {
        public ModelBuilder get()
        {
            return new DefaultModelBuilderFactory().newInstance();
        }
    }
}
