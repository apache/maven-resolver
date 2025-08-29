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
package org.apache.maven.resolver.examples;

import org.apache.maven.resolver.examples.resolver.ResolverDemo;

/**
 * Runs all demos at once.
 */
public class AllResolverDemos {
    /**
     * Main
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        // examples
        FindAvailableVersions.main(args);
        FindNewestVersion.main(args);
        GetDirectDependencies.main(args);
        GetDependencyTree.main(args);
        GetDependencyHierarchy.main(args);
        DependencyHierarchyWithRanges.main(args);
        ResolveArtifact.main(args);
        ResolveTransitiveDependencies.main(args);
        ReverseDependencyTree.main(args);
        InstallArtifacts.main(args);
        DeployArtifacts.main(args);

        // resolver demo
        ResolverDemo.main(args);
    }
}
