package org.eclipse.aether.util.transform;

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

import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.transform.DeployRequestTransformer;

import static java.util.Objects.requireNonNull;

/**
 * A {@link DeployRequestTransformer} that delegates to a chain of other transformers.
 */
public final class ChainedDeployRequestTransformer implements DeployRequestTransformer
{
    private final List<DeployRequestTransformer> transformers;

    public ChainedDeployRequestTransformer( List<DeployRequestTransformer> transformers )
    {
        this.transformers = requireNonNull( transformers );
    }

    @Override
    public DeployRequest transformDeployRequest( RepositorySystemSession session, DeployRequest deployRequest )
    {
        DeployRequest result = deployRequest;
        for ( DeployRequestTransformer transformer : transformers )
        {
            result = transformer.transformDeployRequest( session, result );
        }
        return result;
    }
}
