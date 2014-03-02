/*******************************************************************************
 * Copyright (c) 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.examples.sisu;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.sisu.launch.Main;

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
        return Main.boot( SisuRepositorySystemFactory.class ).repositorySystem;
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
