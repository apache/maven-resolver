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
package org.eclipse.aether.internal.impl.collect.bf;

import java.util.Collections;

import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.internal.impl.StubRemoteRepositoryManager;
import org.eclipse.aether.internal.impl.StubVersionRangeResolver;
import org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegate;
import org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegateTestSupport;

/**
 * UT for {@link BfDependencyCollector}.
 */
public class BfWithoutSkipperDependencyCollectorTest extends DependencyCollectorDelegateTestSupport {
    @Override
    protected DependencyCollectorDelegate setupCollector(ArtifactDescriptorReader artifactDescriptorReader) {
        session.setConfigProperty(BfDependencyCollector.CONFIG_PROP_SKIPPER, false);

        return new BfDependencyCollector(
                new StubRemoteRepositoryManager(),
                artifactDescriptorReader,
                Collections.emptyMap(),
                new StubVersionRangeResolver());
    }

    @Override
    protected String getTransitiveDepsUseRangesDirtyTreeResource() {
        return "transitiveDepsUseRangesDirtyTreeResult_BF.txt";
    }

    @Override
    protected String getTransitiveDepsUseRangesAndRelocationDirtyTreeResource() {
        return "transitiveDepsUseRangesAndRelocationDirtyTreeResult_BF.txt";
    }
}
